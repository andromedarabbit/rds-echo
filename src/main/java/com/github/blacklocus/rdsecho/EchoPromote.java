package com.github.blacklocus.rdsecho;

import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.Endpoint;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.HostedZone;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.github.blacklocus.rdsecho.utl.EchoUtil;
import com.github.blacklocus.rdsecho.utl.Route53Find;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.blacklocus.rdsecho.utl.Route53Find.cnameEquals;
import static com.github.blacklocus.rdsecho.utl.Route53Find.nameEquals;
import static com.google.common.collect.Iterables.getOnlyElement;

public class EchoPromote extends AbstractEchoIntermediateStage {

    private static final Logger LOG = LoggerFactory.getLogger(EchoPromote.class);

    final AmazonRoute53 route53 = new AmazonRoute53Client();
    final Route53Find route53Find = new Route53Find();

    public EchoPromote() {
        super(EchoConst.STAGE_REBOOTED, EchoConst.STAGE_PROMOTED);
    }

    @Override
    boolean traverseStage(DBInstance instance) {

        LOG.info("Reading current DNS records");
        String tld = EchoUtil.getTLD(cfg.promoteCname()) + '.';
        HostedZone hostedZone = route53Find.hostedZone(nameEquals(tld)).get();
        LOG.info("  Found corresponding HostedZone. name: {} id: {}", hostedZone.getName(), hostedZone.getId());

        ResourceRecordSet resourceRecordSet = route53Find.resourceRecordSet(
                hostedZone.getId(), cnameEquals(cfg.promoteCname())).get();
        ResourceRecord resourceRecord = getOnlyElement(resourceRecordSet.getResourceRecords());
        LOG.info("  Found CNAME {} with current value {}", resourceRecordSet.getName(), resourceRecord.getValue());

        Endpoint endpoint = instance.getEndpoint();
        String tagEchoManaged = echo.getTagEchoManaged();
        String dbInstanceId = instance.getDBInstanceIdentifier();
        if (null == endpoint) {
            LOG.info("Echo DB instance {} (id: {}) has no address. Is it still initializing?",
                    tagEchoManaged, dbInstanceId);
            return false;
        }
        String instanceAddr = endpoint.getAddress();
        if (resourceRecord.getValue().equals(instanceAddr)) {
            LOG.info("  Echo DB instance {} ({}) lines up with CNAME {}. Nothing to do.",
                    tagEchoManaged, instanceAddr, resourceRecordSet.getName());
            return false;
        } else {
            LOG.info("  Echo DB instance {} ({}) differs from CNAME {}.",
                    tagEchoManaged, instanceAddr, resourceRecordSet.getName());
        }

        if (cfg.interactive()) {
            String format = "Are you sure you want to promote %s to be the new target of %s? Input %s to confirm.";
            if (!EchoUtil.prompt(dbInstanceId, format, dbInstanceId, cfg.promoteCname(), dbInstanceId)) {
                LOG.info("User declined to proceed. Exiting.");
                return false;
            }
        }

        LOG.info("Updating CNAME {} from {} to {}", cfg.name(), resourceRecord.getValue(), instanceAddr);
        ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withChangeBatch(new ChangeBatch()
                        .withChanges(new Change(ChangeAction.UPSERT, new ResourceRecordSet(cfg.promoteCname(), RRType.CNAME)
                                .withResourceRecords(new ResourceRecord(instanceAddr))
                                .withTTL(cfg.promoteTtl()))));
        route53.changeResourceRecordSets(request);

        LOG.info("Searching for any existing promoted instance to demote.");


        return true;
    }

    public static void main(String[] args) throws Exception {
        new EchoPromote().call();
    }
}