#!/bin/bash
TKZ_PATH="$(dirname "$0")"
java24 --module-path "$TKZ_PATH/lib/BackupStrategies.jar:$TKZ_PATH/TKZ.jar:$TKZ_PATH/lib/GUI.jar:$TKZ_PATH/lib/APIUtils.jar:$TKZ_PATH/lib/IconsStrategies.jar:$TKZ_PATH/lib/ProfileManagement.jar" -m TKZ/ApplicationExecution.Main
