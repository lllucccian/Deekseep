#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 <conversation-id> [instruction] [reminder|heartbeat]" >&2
    exit 2
}

[[ $# -ge 1 ]] || usage

conversation_id=$1
instruction=${2:-"命令触发的心跳测试，请自然地简短回复。"}
task_kind=${3:-reminder}

[[ $conversation_id =~ ^[A-Za-z0-9._-]{4,120}$ ]] || {
    echo "Invalid conversation id: $conversation_id" >&2
    exit 2
}
[[ $task_kind == reminder || $task_kind == heartbeat ]] || usage
[[ -n ${instruction//[[:space:]]/} ]] || {
    echo "Instruction must not be empty" >&2
    exit 2
}

shell_quote() {
    local value=${1//\'/\'\\\'\'}
    printf "'%s'" "$value"
}

task_id="command_$(date +%s)_$$"
command="am broadcast"
command+=" -n com.dsmod.probe/.ProactiveHeartbeatReceiver"
command+=" -a com.dsmod.probe.action.PROACTIVE_TASK_ALARM"
command+=" --es deekseep_proactive_token deekseep-proactive-heartbeat-1f73-19c8bda62374"
command+=" --es task_id $(shell_quote "$task_id")"
command+=" --es task_text $(shell_quote "$instruction")"
command+=" --es task_kind $(shell_quote "$task_kind")"
command+=" --es conversation_id $(shell_quote "$conversation_id")"
command+=" --ez task_reminder true"

su -c "$command"
echo "Triggered $task_kind task $task_id for $conversation_id"
