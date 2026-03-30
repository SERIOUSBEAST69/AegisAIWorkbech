import fs from 'fs';

const reportPath = process.argv[2] || 'e2e-report.json';

if (!fs.existsSync(reportPath)) {
  console.error(`[quality-gate] missing report: ${reportPath}`);
  process.exit(1);
}

const report = JSON.parse(fs.readFileSync(reportPath, 'utf-8'));
const metrics = report.metrics || {};

function metricValue(name, fallback = null) {
  const value = metrics[name];
  if (!value) return fallback;
  return value;
}

const failedRate = metricValue('http_req_failed')?.values?.rate ?? 1;
const p95 = metricValue('http_req_duration')?.values?.['p(95)'] ?? Number.POSITIVE_INFINITY;
const p99 = metricValue('http_req_duration')?.values?.['p(99)'] ?? Number.POSITIVE_INFINITY;

const issues = [];
if (failedRate >= 0.02) issues.push(`http_req_failed.rate=${failedRate}`);
if (p95 >= 900) issues.push(`http_req_duration.p(95)=${p95}`);
if (p99 >= 1400) issues.push(`http_req_duration.p(99)=${p99}`);

if (issues.length > 0) {
  console.error('[quality-gate] FAILED: ' + issues.join('; '));
  process.exit(1);
}

console.log('[quality-gate] PASSED', { failedRate, p95, p99 });
