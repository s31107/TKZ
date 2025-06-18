module BackupsStrategies {
    requires APIUtils;
    requires java.desktop;
    requires java.logging;
    provides API.BackupStrategy with Mirror.MirrorBackup, Mirror.MirrorBackupModificationTime;
}