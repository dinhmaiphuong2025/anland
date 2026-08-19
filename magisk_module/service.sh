#!/system/bin/sh
# Start the anland daemons at boot and keep them alive independently of any
# app: if a daemon exits (crash, kill), restart it so the next container /
# anland consumer connect always finds a live broker.
MODDIR=${0%/*}

# Display daemon (screen fds + screen info broker)
SOCK=/data/local/tmp/display_daemon.sock
while true; do
    rm -f "$SOCK"
    "$MODDIR/display_daemon" "$SOCK"
    sleep 1
done &

# Hardware info daemon (battery/wifi/bt/cpu snapshot over a JSON socket)
HSOCK=/data/local/tmp/hwinfo.sock
while true; do
    rm -f "$HSOCK"
    "$MODDIR/hwinfo_daemon" "$HSOCK"
    sleep 1
done &