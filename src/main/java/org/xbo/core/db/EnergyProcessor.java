package org.xbo.core.db;

import static java.lang.Long.max;

import lombok.extern.slf4j.Slf4j;
import org.xbo.core.capsule.AccountCapsule;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.config.Parameter.AdaptiveResourceLimitConstants;
import org.xbo.core.exception.AccountResourceInsufficientException;
import org.xbo.core.exception.ContractValidateException;
import org.xbo.protos.Protocol.Account.AccountResource;

@Slf4j(topic = "DB")
public class EnergyProcessor extends ResourceProcessor {

  public EnergyProcessor(Manager manager) {
    super(manager);
  }

  @Override
  public void updateUsage(AccountCapsule accountCapsule) {
    long now = dbManager.getWitnessController().getHeadSlot();
    updateUsage(accountCapsule, now);
  }

  private void updateUsage(AccountCapsule accountCapsule, long now) {
    AccountResource accountResource = accountCapsule.getAccountResource();

    long oldEnergyUsage = accountResource.getEnergyUsage();
    long latestConsumeTime = accountResource.getLatestConsumeTimeForEnergy();

    accountCapsule.setEnergyUsage(increase(oldEnergyUsage, 0, latestConsumeTime, now));
  }

  public void updateTotalEnergyAverageUsage() {
    long now = dbManager.getWitnessController().getHeadSlot();
    long blockEnergyUsage = dbManager.getDynamicPropertiesStore().getBlockEnergyUsage();
    long totalEnergyAverageUsage = dbManager.getDynamicPropertiesStore()
        .getTotalEnergyAverageUsage();
    long totalEnergyAverageTime = dbManager.getDynamicPropertiesStore().getTotalEnergyAverageTime();

    long newPublicEnergyAverageUsage = increase(totalEnergyAverageUsage, blockEnergyUsage,
        totalEnergyAverageTime, now, averageWindowSize);

    dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(newPublicEnergyAverageUsage);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageTime(now);
  }

  public void updateAdaptiveTotalEnergyLimit() {
    long totalEnergyAverageUsage = dbManager.getDynamicPropertiesStore()
        .getTotalEnergyAverageUsage();
    long targetTotalEnergyLimit = dbManager.getDynamicPropertiesStore().getTotalEnergyTargetLimit();
    long totalEnergyCurrentLimit = dbManager.getDynamicPropertiesStore()
        .getTotalEnergyCurrentLimit();
    long totalEnergyLimit = dbManager.getDynamicPropertiesStore().getTotalEnergyLimit();

    long result;
    if (totalEnergyAverageUsage > targetTotalEnergyLimit) {
      result = totalEnergyCurrentLimit * AdaptiveResourceLimitConstants.CONTRACT_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.CONTRACT_RATE_DENOMINATOR;
      // logger.info(totalEnergyAverageUsage + ">" + targetTotalEnergyLimit + "\n" + result);
    } else {
      result = totalEnergyCurrentLimit * AdaptiveResourceLimitConstants.EXPAND_RATE_NUMERATOR
          / AdaptiveResourceLimitConstants.EXPAND_RATE_DENOMINATOR;
      // logger.info(totalEnergyAverageUsage + "<" + targetTotalEnergyLimit + "\n" + result);
    }

    result = Math.min(
        Math.max(result, totalEnergyLimit),
        totalEnergyLimit * AdaptiveResourceLimitConstants.LIMIT_MULTIPLIER
    );

    dbManager.getDynamicPropertiesStore().saveTotalEnergyCurrentLimit(result);
    logger.debug(
        "adjust totalEnergyCurrentLimit, old[" + totalEnergyCurrentLimit + "], new[" + result
            + "]");
  }

  @Override
  public void consume(TransactionCapsule xbo,
      TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException {
    throw new RuntimeException("Not support");
  }


  public boolean useEnergy(AccountCapsule accountCapsule, long energy, long now) {

    long energyUsage = accountCapsule.getEnergyUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForEnergy();
    long energyLimit = calculateGlobalEnergyLimit(accountCapsule);

    long newEnergyUsage = increase(energyUsage, 0, latestConsumeTime, now);

    if (energy > (energyLimit - newEnergyUsage)) {
      return false;
    }

    latestConsumeTime = now;
    long latestOperationTime = dbManager.getHeadBlockTimeStamp();
    newEnergyUsage = increase(newEnergyUsage, energy, latestConsumeTime, now);
    accountCapsule.setEnergyUsage(newEnergyUsage);
    accountCapsule.setLatestOperationTime(latestOperationTime);
    accountCapsule.setLatestConsumeTimeForEnergy(latestConsumeTime);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    if (dbManager.getDynamicPropertiesStore().getAllowAdaptiveEnergy() == 1) {
      long blockEnergyUsage = dbManager.getDynamicPropertiesStore().getBlockEnergyUsage() + energy;
      dbManager.getDynamicPropertiesStore().saveBlockEnergyUsage(blockEnergyUsage);
    }

    return true;
  }


  public long calculateGlobalEnergyLimit(AccountCapsule accountCapsule) {
    long frozeBalance = accountCapsule.getAllFrozenBalanceForEnergy();
    if (frozeBalance < 1_000_000L) {
      return 0;
    }

    long energyWeight = frozeBalance / 1_000_000L;
    long totalEnergyLimit = dbManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit();
    long totalEnergyWeight = dbManager.getDynamicPropertiesStore().getTotalEnergyWeight();

    assert totalEnergyWeight > 0;

    return (long) (energyWeight * ((double) totalEnergyLimit / totalEnergyWeight));
  }

  public long getAccountLeftEnergyFromFreeze(AccountCapsule accountCapsule) {

    long now = dbManager.getWitnessController().getHeadSlot();

    long energyUsage = accountCapsule.getEnergyUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForEnergy();
    long energyLimit = calculateGlobalEnergyLimit(accountCapsule);

    long newEnergyUsage = increase(energyUsage, 0, latestConsumeTime, now);

    return max(energyLimit - newEnergyUsage, 0); // us
  }

}


