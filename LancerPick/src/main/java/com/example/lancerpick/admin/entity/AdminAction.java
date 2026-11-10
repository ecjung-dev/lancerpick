package com.example.lancerpick.admin.entity;

public enum AdminAction {
  TOPUP_RECHECK("WALLET_TOPUP"),
  TOPUP_RECOVERY_RESTART("BATCH_JOB_EXECUTION"),
  CONTRACT_PDF_REGENERATE("CONTRACT");

  private final String targetType;

  AdminAction(String targetType) {
    this.targetType = targetType;
  }

  public String targetType() {
    return targetType;
  }
}
