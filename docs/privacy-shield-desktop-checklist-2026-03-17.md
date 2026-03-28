# Privacy Shield Desktop Checklist (2026-03-17)

## Preconditions
- Backend is running at http://localhost:8080.
- AI inference service is running at http://localhost:5000.
- Electron client is running from electron/main.js.
- Browser extension has been loaded from browser-extension/privacy-shield.

## Account
- Use employee.demo / demo1234 for user-level verification.

## Case 1: Non-AI Window Copy (Word/Notepad)
1. Open Notepad or Word.
2. Copy text containing sensitive pattern, for example: 身份证 110101199001011234.
3. Keep focus on Notepad or Word for at least 3 seconds.

Expected:
- No desktop warning popup.
- Event may be uploaded as audit-only, but user should not be interrupted.

## Case 2: AI Window Copy (ChatGPT/Doubao/Yiyan)
1. Open ChatGPT, Doubao, or Yiyan page and make this window active.
2. Copy sensitive text again.
3. Keep AI window focused for at least 3 seconds.

Expected:
- Desktop warning popup appears.
- Privacy event is uploaded with source=clipboard.

## Case 3: Browser Input Banner
1. On ChatGPT/Doubao/Yiyan input box, paste or type sensitive text.
2. Wait for detection debounce (about 0.5 seconds).

Expected:
- Floating banner appears with warning text.
- Matched type hint is shown when available.

## Case 4: One-Click Desensitize
1. In the banner, click 一键脱敏.

Expected:
- Input content is rewritten to masked text.
- A privacy event is uploaded with action=desensitize.

## Case 5: Ignore Warning
1. Enter sensitive text again.
2. Click 忽略警告.

Expected:
- Banner closes and input is kept unchanged.
- A privacy event is uploaded with action=ignore.

## Case 6: Role-Scoped Visibility
1. Login as employee.demo and open /ai/anomaly -> 隐私盾告警 tab.
2. Login as exec.demo and open the same tab.
3. Login as admin and secops.demo and open the same tab.

Expected:
- employee.demo: personal records only.
- exec.demo: summary-only, no list details.
- admin/secops.demo: full records and config management panel.

## Recording Template
- Tester:
- DateTime:
- Environment:
- Case 1 pass/fail:
- Case 2 pass/fail:
- Case 3 pass/fail:
- Case 4 pass/fail:
- Case 5 pass/fail:
- Case 6 pass/fail:
- Notes:
