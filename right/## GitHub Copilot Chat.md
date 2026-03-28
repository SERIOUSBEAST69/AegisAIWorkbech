## GitHub Copilot Chat

- Extension: 0.37.6 (prod)
- VS Code: 1.109.3 (b6a47e94e326b5c209d118cf0f994d6065585705)
- OS: win32 10.0.26200 x64
- GitHub Account: SERIOUSBEAST69

## Network

User Settings:
```json
  "http.systemCertificatesNode": false,
  "github.copilot.advanced.debug.useElectronFetcher": true,
  "github.copilot.advanced.debug.useNodeFetcher": false,
  "github.copilot.advanced.debug.useNodeFetchFetcher": true
```

Connecting to https://api.github.com:
- DNS ipv4 Lookup: 20.205.243.168 (11 ms)
- DNS ipv6 Lookup: Error (21 ms): getaddrinfo ENOTFOUND api.github.com
- Proxy URL: http://127.0.0.1:7890 (1 ms)
- Proxy Connection: Error (2 ms): connect ECONNREFUSED 127.0.0.1:7890
- Electron fetch (configured): Error (2034 ms): Error: net::ERR_PROXY_CONNECTION_FAILED
	at SimpleURLLoaderWrapper.<anonymous> (node:electron/js2c/utility_init:2:10684)
	at SimpleURLLoaderWrapper.emit (node:events:519:28)
  [object Object]
  {"is_request_error":true,"network_process_crashed":false}
- Node.js https: Error (2 ms): Error: Failed to establish a socket connection to proxies: PROXY 127.0.0.1:7890
	at PacProxyAgent.<anonymous> (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:120:19)
	at Generator.throw (<anonymous>)
	at rejected (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:6:65)
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
- Node.js fetch: Error (4 ms): TypeError: fetch failed
	at node:internal/deps/undici/undici:14900:13
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
	at async n._fetch (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4862:26169)
	at async n.fetch (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4862:25817)
	at async u (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4894:190)
	at async CA.h (file:///d:/cxdownload/Microsoft%20VS%20Code/b6a47e94e3/resources/app/out/vs/workbench/api/node/extensionHostProcess.js:116:41743)
  Error: connect ECONNREFUSED 127.0.0.1:7890
  	at TCPConnectWrap.afterConnect [as oncomplete] (node:net:1637:16)

Connecting to https://api.githubcopilot.com/_ping:
- DNS ipv4 Lookup: 140.82.113.22 (9 ms)
- DNS ipv6 Lookup: Error (7 ms): getaddrinfo ENOTFOUND api.githubcopilot.com
- Proxy URL: http://127.0.0.1:7890 (2 ms)
- Proxy Connection: Error (1 ms): connect ECONNREFUSED 127.0.0.1:7890
- Electron fetch (configured): Error (2038 ms): Error: net::ERR_PROXY_CONNECTION_FAILED
	at SimpleURLLoaderWrapper.<anonymous> (node:electron/js2c/utility_init:2:10684)
	at SimpleURLLoaderWrapper.emit (node:events:519:28)
  [object Object]
  {"is_request_error":true,"network_process_crashed":false}
- Node.js https: Error (1 ms): Error: Failed to establish a socket connection to proxies: PROXY 127.0.0.1:7890
	at PacProxyAgent.<anonymous> (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:120:19)
	at Generator.throw (<anonymous>)
	at rejected (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:6:65)
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
- Node.js fetch: Error (5 ms): TypeError: fetch failed
	at node:internal/deps/undici/undici:14900:13
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
	at async n._fetch (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4862:26169)
	at async n.fetch (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4862:25817)
	at async u (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4894:190)
	at async CA.h (file:///d:/cxdownload/Microsoft%20VS%20Code/b6a47e94e3/resources/app/out/vs/workbench/api/node/extensionHostProcess.js:116:41743)
  Error: connect ECONNREFUSED 127.0.0.1:7890
  	at TCPConnectWrap.afterConnect [as oncomplete] (node:net:1637:16)

Connecting to https://copilot-proxy.githubusercontent.com/_ping:
- DNS ipv4 Lookup: 4.249.131.160 (9 ms)
- DNS ipv6 Lookup: Error (8 ms): getaddrinfo ENOTFOUND copilot-proxy.githubusercontent.com
- Proxy URL: http://127.0.0.1:7890 (6 ms)
- Proxy Connection: Error (1 ms): connect ECONNREFUSED 127.0.0.1:7890
- Electron fetch (configured): Error (2041 ms): Error: net::ERR_PROXY_CONNECTION_FAILED
	at SimpleURLLoaderWrapper.<anonymous> (node:electron/js2c/utility_init:2:10684)
	at SimpleURLLoaderWrapper.emit (node:events:519:28)
  [object Object]
  {"is_request_error":true,"network_process_crashed":false}
- Node.js https: Error (1 ms): Error: Failed to establish a socket connection to proxies: PROXY 127.0.0.1:7890
	at PacProxyAgent.<anonymous> (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:120:19)
	at Generator.throw (<anonymous>)
	at rejected (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:6:65)
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
- Node.js fetch: Error (4 ms): TypeError: fetch failed
	at node:internal/deps/undici/undici:14900:13
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
	at async n._fetch (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4862:26169)
	at async n.fetch (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4862:25817)
	at async u (c:\Users\serio\.vscode\extensions\github.copilot-chat-0.37.6\dist\extension.js:4894:190)
	at async CA.h (file:///d:/cxdownload/Microsoft%20VS%20Code/b6a47e94e3/resources/app/out/vs/workbench/api/node/extensionHostProcess.js:116:41743)
  Error: connect ECONNREFUSED 127.0.0.1:7890
  	at TCPConnectWrap.afterConnect [as oncomplete] (node:net:1637:16)

Connecting to https://mobile.events.data.microsoft.com: Error (2034 ms): Error: net::ERR_PROXY_CONNECTION_FAILED
	at SimpleURLLoaderWrapper.<anonymous> (node:electron/js2c/utility_init:2:10684)
	at SimpleURLLoaderWrapper.emit (node:events:519:28)
  [object Object]
  {"is_request_error":true,"network_process_crashed":false}
Connecting to https://dc.services.visualstudio.com: Error (2030 ms): Error: net::ERR_PROXY_CONNECTION_FAILED
	at SimpleURLLoaderWrapper.<anonymous> (node:electron/js2c/utility_init:2:10684)
	at SimpleURLLoaderWrapper.emit (node:events:519:28)
  [object Object]
  {"is_request_error":true,"network_process_crashed":false}
Connecting to https://copilot-telemetry.githubusercontent.com/_ping: Error (2 ms): Error: Failed to establish a socket connection to proxies: PROXY 127.0.0.1:7890
	at PacProxyAgent.<anonymous> (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:120:19)
	at Generator.throw (<anonymous>)
	at rejected (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:6:65)
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
Connecting to https://copilot-telemetry.githubusercontent.com/_ping: Error (2 ms): Error: Failed to establish a socket connection to proxies: PROXY 127.0.0.1:7890
	at PacProxyAgent.<anonymous> (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:120:19)
	at Generator.throw (<anonymous>)
	at rejected (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:6:65)
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)
Connecting to https://default.exp-tas.com: Error (2 ms): Error: Failed to establish a socket connection to proxies: PROXY 127.0.0.1:7890
	at PacProxyAgent.<anonymous> (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:120:19)
	at Generator.throw (<anonymous>)
	at rejected (d:\cxdownload\Microsoft VS Code\b6a47e94e3\resources\app\node_modules\@vscode\proxy-agent\out\agent.js:6:65)
	at process.processTicksAndRejections (node:internal/process/task_queues:105:5)

Number of system certificates: 181

## Documentation

In corporate networks: [Troubleshooting firewall settings for GitHub Copilot](https://docs.github.com/en/copilot/troubleshooting-github-copilot/troubleshooting-firewall-settings-for-github-copilot).