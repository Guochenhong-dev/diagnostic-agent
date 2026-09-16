"""Read-only MCP stdio adapter. No third-party Python dependencies.
JSON-RPC lines over stdin/stdout; stderr is reserved for diagnostics.
"""
import json, os, sys, urllib.request, urllib.parse, urllib.error

BASE = os.environ.get('DIAG_URL', 'http://127.0.0.1:8083').rstrip('/')
TOKEN = os.environ.get('APP_TOKEN', 'local-demo-token')
TOOLS = [
    {'name': 'list_projects', 'description': 'List registered local source projects', 'inputSchema': {'type': 'object', 'properties': {}, 'additionalProperties': False}},
    {'name': 'get_diagnosis', 'description': 'Read a persisted diagnosis and its evidence', 'inputSchema': {'type': 'object', 'properties': {'id': {'type': 'string'}}, 'required': ['id'], 'additionalProperties': False}},
    {'name': 'read_source', 'description': 'Read redacted Java/XML/properties source within one registered project', 'inputSchema': {'type': 'object', 'properties': {'project': {'type': 'string'}, 'path': {'type': 'string'}}, 'required': ['project', 'path'], 'additionalProperties': False}},
]

def get(path):
    req = urllib.request.Request(BASE + path, headers={'X-App-Token': TOKEN})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.load(r)

def dispatch(msg):
    method, params = msg.get('method'), msg.get('params', {})
    if method == 'initialize':
        version = params.get('protocolVersion', '2024-11-05')
        if version not in ('2024-11-05', '2025-03-26', '2025-06-18'):
            version = '2024-11-05'
        return {'protocolVersion': version, 'capabilities': {'tools': {}}, 'serverInfo': {'name': 'java-diagnostic-readonly', 'version': '1.0.0'}}
    if method == 'ping': return {}
    if method == 'tools/list': return {'tools': TOOLS}
    if method == 'tools/call':
        name, args = params.get('name'), params.get('arguments', {})
        try:
            if name == 'list_projects': data = get('/api/projects')
            elif name == 'get_diagnosis': data = get('/api/runs/' + urllib.parse.quote(args['id'], safe=''))
            elif name == 'read_source': data = get('/api/source?' + urllib.parse.urlencode({'project': args['project'], 'path': args['path']}))
            else: raise ValueError('Unknown tool')
            return {'content': [{'type': 'text', 'text': json.dumps(data, ensure_ascii=False)}]}
        except Exception as e:
            return {'isError': True, 'content': [{'type': 'text', 'text': type(e).__name__ + ': tool request failed'}]}
    raise ValueError('Method not found')

def main():
    for line in sys.stdin:
        try:
            msg = json.loads(line)
            if 'id' not in msg: continue
            try: out = {'jsonrpc': '2.0', 'id': msg['id'], 'result': dispatch(msg)}
            except ValueError: out = {'jsonrpc': '2.0', 'id': msg['id'], 'error': {'code': -32601, 'message': 'Method not found'}}
        except Exception:
            out = {'jsonrpc': '2.0', 'id': None, 'error': {'code': -32700, 'message': 'Parse error'}}
        print(json.dumps(out, ensure_ascii=False), flush=True)

if __name__ == '__main__': main()
