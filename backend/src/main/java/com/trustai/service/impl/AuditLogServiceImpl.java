package com.trustai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.RabbitConfig;
import com.trustai.document.AuditLogDocument;
import com.trustai.entity.AuditLog;
import com.trustai.exception.BizException;
import com.trustai.mapper.AuditLogMapper;
import com.trustai.repository.AuditLogEsRepository;
import com.trustai.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String SYSTEM_AUDIT_USERNAME = "system";
	private static final String ACTOR_TYPE_SYSTEM = "system";
	private static final String ACTOR_TYPE_USER = "user";
	/** 单次搜索最大返回条数，防止全量扫描 */
	private static final int MAX_RESULTS = 1000;

	private final RabbitTemplate rabbitTemplate;
	private final ObjectProvider<AuditLogEsRepository> auditLogEsRepositoryProvider;
	private final ObjectProvider<ElasticsearchOperations> elasticsearchOperationsProvider;
	private final JdbcTemplate jdbcTemplate;

	@Override
	public boolean save(AuditLog entity) {
		normalizeAuditActor(entity);
		boolean db = super.save(entity);
		appendAuditHashChain(entity);
		sendAsync(entity);
		return db;
	}

	@Override
	public boolean saveAudit(AuditLog log) {
		normalizeAuditActor(log);
		boolean db = super.save(log);
		appendAuditHashChain(log);
		sendAsync(log);
		return db;
	}

	private void normalizeAuditActor(AuditLog entity) {
		if (entity == null) {
			throw new BizException(40000, "审计日志不能为空");
		}
		if (entity.getUserId() == null || entity.getUserId() <= 0) {
			entity.setUserId(resolveSystemAuditUserId());
			entity.setActorType(ACTOR_TYPE_SYSTEM);
			return;
		}
		Integer exists = jdbcTemplate.queryForObject(
			"SELECT COUNT(1) FROM sys_user WHERE id = ?",
			Integer.class,
			entity.getUserId()
		);
		if (exists == null || exists == 0) {
			throw new BizException(40000, "审计日志 user_id 无效");
		}
		entity.setActorType(ACTOR_TYPE_USER);
	}

	private Long resolveSystemAuditUserId() {
		Long systemUserId = jdbcTemplate.query(
			"SELECT id FROM sys_user WHERE LOWER(username) = ? ORDER BY id ASC LIMIT 1",
			ps -> ps.setString(1, SYSTEM_AUDIT_USERNAME),
			rs -> rs.next() ? rs.getObject(1, Long.class) : null
		);
		if (systemUserId != null) {
			return systemUserId;
		}
		throw new BizException(40000, "系统审计账号未初始化，请先执行数据初始化");
	}

	private void appendAuditHashChain(AuditLog logEntity) {
		if (logEntity == null || logEntity.getId() == null) {
			return;
		}
		try {
			Integer exists = jdbcTemplate.queryForObject(
				"SELECT COUNT(1) FROM audit_hash_chain WHERE audit_log_id = ?",
				Integer.class,
				logEntity.getId()
			);
			if (exists != null && exists > 0) {
				return;
			}

			AuditLog persisted = jdbcTemplate.query(
				"SELECT id, user_id, operation, operation_time, input_overview, output_overview, result FROM audit_log WHERE id = ? LIMIT 1",
				ps -> ps.setLong(1, logEntity.getId()),
				rs -> {
					if (!rs.next()) {
						return null;
					}
					AuditLog row = new AuditLog();
					row.setId(rs.getLong("id"));
					row.setUserId(rs.getObject("user_id", Long.class));
					row.setOperation(rs.getString("operation"));
					row.setOperationTime(rs.getTimestamp("operation_time"));
					row.setInputOverview(rs.getString("input_overview"));
					row.setOutputOverview(rs.getString("output_overview"));
					row.setResult(rs.getString("result"));
					return row;
				}
			);
			if (persisted == null || persisted.getUserId() == null) {
				return;
			}

			Long companyId = resolveCompanyId(persisted.getUserId());
			if (companyId == null || companyId <= 0L) {
				return;
			}

			String prevHash = jdbcTemplate.query(
				"SELECT current_hash FROM audit_hash_chain WHERE company_id = ? ORDER BY id DESC LIMIT 1",
				ps -> ps.setLong(1, companyId),
				rs -> rs.next() ? rs.getString(1) : null
			);
			String currentHash = sha256(buildChainPayload(persisted, companyId, prevHash));

			jdbcTemplate.update(
				"INSERT INTO audit_hash_chain(company_id, audit_log_id, prev_hash, current_hash, create_time) VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP)",
				companyId,
				persisted.getId(),
				prevHash,
				currentHash
			);
		} catch (Exception ex) {
			log.warn("append audit hash chain failed for logId={}", logEntity.getId(), ex);
		}
	}

	private Long resolveCompanyId(Long userId) {
		if (userId == null) {
			return null;
		}
		return jdbcTemplate.query(
			"SELECT company_id FROM sys_user WHERE id = ? LIMIT 1",
			ps -> ps.setLong(1, userId),
			rs -> rs.next() ? rs.getObject(1, Long.class) : null
		);
	}

	private String buildChainPayload(AuditLog logEntity, Long companyId, String prevHash) {
		return String.join("|",
			String.valueOf(companyId),
			String.valueOf(logEntity.getId()),
			String.valueOf(logEntity.getUserId()),
			safe(logEntity.getOperation()),
			safe(normalizeEpochMillis(logEntity.getOperationTime())),
			safe(logEntity.getInputOverview()),
			safe(logEntity.getOutputOverview()),
			safe(logEntity.getResult()),
			safe(prevHash)
		);
	}

	private String normalizeEpochMillis(Date operationTime) {
		if (operationTime == null) {
			return null;
		}
		long secondAligned = (operationTime.getTime() / 1000L) * 1000L;
		return String.valueOf(secondAligned);
	}

	private String sha256(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : bytes) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Exception ex) {
			throw new IllegalStateException("sha256 compute failed", ex);
		}
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private void sendAsync(AuditLog auditLog) {
		if (auditLog == null) {
			return;
		}
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					publishAuditMessage(auditLog);
				}
			});
			return;
		}
		publishAuditMessage(auditLog);
	}

	private void publishAuditMessage(AuditLog auditLog) {
		try {
			rabbitTemplate.convertAndSend(RabbitConfig.AUDIT_LOG_QUEUE, MAPPER.writeValueAsString(auditLog));
		} catch (Exception ex) {
			log.warn("publish audit message failed, logId={}", auditLog.getId(), ex);
		}
	}

	/**
	 * ES 原生查询优化：将过滤条件下推至 Elasticsearch，避免全量拉取后在内存中过滤。
	 * 使用 {@link CriteriaQuery} 构建 bool 查询，最多返回 {@value #MAX_RESULTS} 条。
	 */
	@Override
	public List<AuditLogDocument> search(Long userId, Long permissionId, String operation, Date from, Date to) {
		String keyword = (operation == null) ? "" : operation.trim().toLowerCase(Locale.ROOT);

		Criteria criteria = null;

		if (userId != null) {
			criteria = new Criteria("userId").is(userId);
		}

		if (permissionId != null) {
			Criteria permissionCriteria = new Criteria("permissionId").is(permissionId);
			criteria = (criteria == null) ? permissionCriteria : criteria.and(permissionCriteria);
		}

		if (StringUtils.hasText(keyword)) {
			Criteria textCriteria = new Criteria("operation").contains(keyword)
					.or(new Criteria("inputOverview").contains(keyword))
					.or(new Criteria("outputOverview").contains(keyword))
					.or(new Criteria("result").contains(keyword));
			criteria = (criteria == null) ? textCriteria : criteria.and(textCriteria);
		}

		if (from != null) {
			Criteria fromCriteria = new Criteria("operationTime").greaterThanEqual(from);
			criteria = (criteria == null) ? fromCriteria : criteria.and(fromCriteria);
		}

		if (to != null) {
			Criteria toCriteria = new Criteria("operationTime").lessThanEqual(to);
			criteria = (criteria == null) ? toCriteria : criteria.and(toCriteria);
		}

		CriteriaQuery query = new CriteriaQuery(
				criteria != null ? criteria : new Criteria(),
				PageRequest.of(0, MAX_RESULTS)
		);

		ElasticsearchOperations elasticsearchOperations = elasticsearchOperationsProvider.getIfAvailable();
		if (elasticsearchOperations == null) {
			return fallbackSearch(userId, permissionId, keyword, from, to);
		}

		try {
			List<AuditLogDocument> esResults = elasticsearchOperations.search(query, AuditLogDocument.class)
					.stream()
					.map(SearchHit::getContent)
					.collect(Collectors.toList());
			if (!esResults.isEmpty()) {
				return esResults;
			}

			List<AuditLogDocument> dbResults = dbFallbackSearch(userId, permissionId, keyword, from, to);
			if (!dbResults.isEmpty()) {
				log.warn("ES returned empty audit logs, fallback to DB returned {} rows", dbResults.size());
				return dbResults;
			}
			return esResults;
		} catch (Exception e) {
			log.warn("ES native query failed, falling back to findAll filter", e);
			// 降级：全量拉取后内存过滤
			return fallbackSearch(userId, permissionId, keyword, from, to);
		}
	}

	private List<AuditLogDocument> fallbackSearch(Long userId, Long permissionId, String normalizedOperation, Date from, Date to) {
		AuditLogEsRepository auditLogEsRepository = auditLogEsRepositoryProvider.getIfAvailable();
		if (auditLogEsRepository == null) {
			return dbFallbackSearch(userId, permissionId, normalizedOperation, from, to);
		}

		try {
			return StreamSupport.stream(auditLogEsRepository.findAll().spliterator(), false)
					.filter(doc -> userId == null || userId.equals(doc.getUserId()))
					.filter(doc -> permissionId == null || permissionId.equals(doc.getPermissionId()))
					.filter(doc -> normalizedOperation.isEmpty()
							|| containsIgnoreCase(doc.getOperation(), normalizedOperation)
							|| containsIgnoreCase(doc.getInputOverview(), normalizedOperation)
							|| containsIgnoreCase(doc.getOutputOverview(), normalizedOperation)
							|| containsIgnoreCase(doc.getResult(), normalizedOperation))
					.filter(doc -> from == null || (doc.getOperationTime() != null && !doc.getOperationTime().before(from)))
					.filter(doc -> to == null || (doc.getOperationTime() != null && !doc.getOperationTime().after(to)))
					.limit(MAX_RESULTS)
					.collect(Collectors.toList());
		} catch (Exception e) {
			log.warn("ES fallback search failed (Elasticsearch 可能未启动), falling back to DB query", e);
			return dbFallbackSearch(userId, permissionId, normalizedOperation, from, to);
		}
	}

	private List<AuditLogDocument> dbFallbackSearch(Long userId, Long permissionId, String normalizedOperation, Date from, Date to) {
		LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
		if (userId != null) {
			qw.eq(AuditLog::getUserId, userId);
		}
		if (permissionId != null) {
			qw.eq(AuditLog::getPermissionId, permissionId);
		}
		if (StringUtils.hasText(normalizedOperation)) {
			qw.and(w -> w.like(AuditLog::getOperation, normalizedOperation)
				.or().like(AuditLog::getInputOverview, normalizedOperation)
				.or().like(AuditLog::getOutputOverview, normalizedOperation)
				.or().like(AuditLog::getResult, normalizedOperation));
		}
		if (from != null) {
			qw.ge(AuditLog::getOperationTime, from);
		}
		if (to != null) {
			qw.le(AuditLog::getOperationTime, to);
		}
		qw.orderByDesc(AuditLog::getOperationTime).last("LIMIT " + MAX_RESULTS);

		return this.list(qw)
			.stream()
			.map(this::toDocument)
			.collect(Collectors.toList());
	}

	private AuditLogDocument toDocument(AuditLog entity) {
		AuditLogDocument doc = new AuditLogDocument();
		doc.setId(entity.getId() == null ? null : String.valueOf(entity.getId()));
		doc.setLogId(entity.getId());
		doc.setUserId(entity.getUserId());
		doc.setUserIdStr(entity.getUserId() == null ? null : String.valueOf(entity.getUserId()));
		doc.setAssetId(entity.getAssetId());
		doc.setPermissionId(entity.getPermissionId());
		doc.setPermissionName(entity.getPermissionName());
		doc.setOperation(entity.getOperation());
		doc.setOperationTime(entity.getOperationTime());
		doc.setIp(entity.getIp());
		doc.setDevice(entity.getDevice());
		doc.setInputOverview(entity.getInputOverview());
		doc.setOutputOverview(entity.getOutputOverview());
		doc.setResult(entity.getResult());
		doc.setRiskLevel(entity.getRiskLevel());
		doc.setHash(entity.getHash());
		doc.setCreateTime(entity.getCreateTime());
		doc.setActorType(resolveActorType(entity.getUserId()));
		return doc;
	}

	private String resolveActorType(Long userId) {
		if (userId == null) {
			return ACTOR_TYPE_USER;
		}
		Long systemUserId = jdbcTemplate.query(
			"SELECT id FROM sys_user WHERE LOWER(username) = ? ORDER BY id ASC LIMIT 1",
			ps -> ps.setString(1, SYSTEM_AUDIT_USERNAME),
			rs -> rs.next() ? rs.getObject(1, Long.class) : null
		);
		return systemUserId != null && systemUserId.equals(userId) ? ACTOR_TYPE_SYSTEM : ACTOR_TYPE_USER;
	}

	private boolean containsIgnoreCase(String source, String normalizedKeyword) {
		return source != null && source.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
	}
}
