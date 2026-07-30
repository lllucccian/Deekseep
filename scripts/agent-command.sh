#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    cat >&2 <<'EOF'
Usage:
  agent-command.sh '{"tool":"capture_screen"}'
  agent-command.sh '{"tool":"ask_user","questions":[{"question":"先做哪项？","options":["界面","执行器","测试"]}]}'
  agent-command.sh '{"tool":"read_file","path":"/data/local/tmp/demo.txt"}'
  agent-command.sh '{"tool":"write_file","path":"/data/local/tmp/demo.txt","content":"hello","create_parents":true}'
  agent-command.sh '{"tool":"shell","command":"which cp && id"}'
  agent-command.sh '{"visible_text":"这是一条命令发送的当前对话消息"}'
EOF
    exit 2
fi

payload_b64=$(
    printf '%s' "$1" \
        | base64 \
        | tr -d '\r\n'
)

su -c "/system/bin/am broadcast \
  -n com.deepseek.chat/com.deepseek.chat.system.ShareResultReceiver \
  -a com.dsmod.probe.action.AGENT_COMMAND \
  --es deekseep_control_token deekseep-local-api-keepalive-v1 \
  --es agent_command_base64 '$payload_b64'"
