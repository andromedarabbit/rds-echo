package com.blacklocus.rds;


import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSnapshot;
import com.amazonaws.services.rds.model.RestoreDBInstanceFromDBSnapshotRequest;
import com.amazonaws.services.rds.model.Tag;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.blacklocus.rds.utl.DbEchoUtil;
import com.blacklocus.rds.utl.RdsFind;
import com.blacklocus.rds.utl.Route53Find;
import com.google.common.base.Optional;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static com.google.common.collect.Iterables.getOnlyElement;

/**
 *
 */
public class DbEchoNew implements Callable<Void> {

    private static final Logger LOG = LoggerFactory.getLogger(DbEchoNew.class);

    final AmazonRoute53 route53 = new AmazonRoute53Client();
    final AmazonRDS rds = new AmazonRDSClient();
    final Route53Find route53Find = new Route53Find();
    final RdsFind rdsFind = new RdsFind();

    final DbEchoCfg cfg = new DbEchoCfg();
    final DbEchoUtil echo = new DbEchoUtil();

    @Override
    public Void call() throws Exception {

        String tagEchoManaged = echo.getTagEchoManaged();

        LOG.info("Checking to see if current echo-created instance {} was created less than 24 hours ago. " +
                "If so this operation will not continue.", tagEchoManaged);
        Optional<DBInstance> newestInstanceOpt = echo.lastEchoInstance();
        if (newestInstanceOpt.isPresent()) {

            if (new DateTime(newestInstanceOpt.get().getInstanceCreateTime()).plusHours(24).isAfter(DateTime.now())) {
                LOG.info("  Last echo-created RDS instance {} was created less than 24 hours ago. Aborting.",
                        tagEchoManaged);
                return null;

            } else {
                LOG.info("  Last echo-created RDS instance {} was created more than 24 hours ago. Proceeding.",
                        tagEchoManaged);
            }

        } else {
            LOG.info("  No prior echo-created instance found with tag {}. Proceeding.", tagEchoManaged);
        }

        LOG.info("Locating latest snapshot from {}", cfg.snapshotDbInstanceIdentifier());
        Optional<DBSnapshot> dbSnapshotOpt = echo.latestSnapshot();
        if (dbSnapshotOpt.isPresent()) {
            DBSnapshot snapshot = dbSnapshotOpt.get();
            LOG.info("  Located snapshot {} completed on {}", snapshot.getDBSnapshotIdentifier(),
                    new DateTime(snapshot.getSnapshotCreateTime()).toDateTimeISO().toString());

        } else {
            LOG.info("  Could not locate a suitable snapshot. Cannot continue.");
            return null;
        }

        String dbSnapshotIdentifier = dbSnapshotOpt.get().getDBSnapshotIdentifier();
        String newDbInstanceIdentifier = cfg.name() + '-' + DateTime.now(DateTimeZone.UTC).toString("yyyy-MM-dd");
        LOG.info("Proposed new db instance...\n" +
                        "  engine           : {}\n" +
                        "  license model    : {}\n" +
                        "  db instance class: {}\n" +
                        "  multi az         : {}\n" +
                        "  storage type     : {}\n" +
                        "  iops             : {}\n" +
                        "  db snapshot id   : {}\n" +
                        "  db instance id   : {}\n" +
                        "  port             : {}\n" +
                        "  option group name: {}\n" +
                        "  auto minor ver up: {}",
                cfg.newEngine(),
                cfg.newLicenseModel(),
                cfg.newDbInstanceClass(),
                cfg.newMultiAz(),
                cfg.newStorageType(),
                cfg.newIops(),
                dbSnapshotIdentifier,
                newDbInstanceIdentifier,
                cfg.newPort(),
                cfg.newOptionGroupName(),
                cfg.newAutoMinorVersionUpgrade());
        if (cfg.interactive()) {
            String format = "Proceed to create a new DB instance from this snapshot? Input %s to confirm.";
            if (!DbEchoUtil.prompt(newDbInstanceIdentifier, format, newDbInstanceIdentifier)) {
                LOG.info("User declined to proceed. Exiting.");
                return null;
            }
        }

        LOG.info("Creating new DB instance. Hold on to your butts.");
        RestoreDBInstanceFromDBSnapshotRequest request = new RestoreDBInstanceFromDBSnapshotRequest()
                .withEngine(cfg.newEngine())
                .withLicenseModel(cfg.newLicenseModel())
                .withDBInstanceClass(cfg.newDbInstanceClass())
                .withMultiAZ(cfg.newMultiAz())
                .withStorageType(cfg.newStorageType())
                .withIops(cfg.newIops())
                .withDBSnapshotIdentifier(dbSnapshotIdentifier)
                .withDBInstanceIdentifier(newDbInstanceIdentifier)
                .withPort(cfg.newPort())
                .withOptionGroupName(cfg.newOptionGroupName())
                .withAutoMinorVersionUpgrade(cfg.newAutoMinorVersionUpgrade())
                .withTags(
                        new Tag().withKey(echo.getTagEchoManaged()).withValue("true"),
                        new Tag().withKey(echo.getTagEchoStage()).withValue(DbEchoConst.STAGE_INITIALIZING)
                );
        DBInstance instance = rds.restoreDBInstanceFromDBSnapshot(request);

        LOG.info("Created new DB instance. \n" +
                        "  https://console.aws.amazon.com/rds/home?region={}#dbinstance:id={}\n" +
                        "Additional preparation of the instance will continue once the instance becomes available.",
                cfg.region(), newDbInstanceIdentifier);

        return null;
    }

    public static void main(String[] args) throws Exception {
        new DbEchoNew().call();
    }
}