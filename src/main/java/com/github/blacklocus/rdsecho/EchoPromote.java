/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 BlackLocus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.blacklocus.rdsecho;

import com.amazonaws.services.rds.model.*;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.HostedZone;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.github.blacklocus.rdsecho.utl.EchoUtil;
import com.github.blacklocus.rdsecho.utl.RdsFind;
import com.github.blacklocus.rdsecho.utl.Route53Find;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.blacklocus.rdsecho.utl.Route53Find.cnameEquals;
import static com.github.blacklocus.rdsecho.utl.Route53Find.nameEquals;
import static com.google.common.collect.Iterables.getOnlyElement;

public class EchoPromote extends AbstractEchoIntermediateStage {

    private static final Logger LOG = LoggerFactory.getLogger(EchoPromote.class);

    final AmazonRoute53 route53 = AmazonRoute53ClientBuilder.defaultClient();
    final Route53Find route53Find = new Route53Find(route53);

    public EchoPromote() {
        super(EchoConst.STAGE_REBOOTED, EchoConst.STAGE_PROMOTED);
    }

    @Override
    boolean traverseStage(DBInstance instance) {

        LOG.info("[{}] Reading current DNS records", getCommand());
        String tld = EchoUtil.getTLD(cfg.promoteCname()) + '.';
        HostedZone hostedZone = route53Find.hostedZone(nameEquals(tld)).get();
        LOG.info("[{}] Found corresponding HostedZone. name: {} id: {}", getCommand(), hostedZone.getName(), hostedZone.getId());

        Optional<ResourceRecordSet> resourceRecordSetOpt = route53Find.resourceRecordSet(
                hostedZone.getId(), cnameEquals(cfg.promoteCname()));

        Endpoint endpoint = instance.getEndpoint();
        String tagEchoManaged = echo.getTagEchoManaged();
        String dbInstanceId = instance.getDBInstanceIdentifier();
        if (null == endpoint) {
            LOG.info("[{}] Echo DB instance {} (id: {}) has no address. Is it still initializing?",
                    getCommand(), tagEchoManaged, dbInstanceId);
            return false;
        }
        String instanceAddr = endpoint.getAddress();

        if( !resourceRecordSetOpt.isPresent() ) {
            ResourceRecordSet recordSet = new ResourceRecordSet(cfg.promoteCname(), "CNAME");

            ResourceRecord record = new ResourceRecord(instanceAddr + ".notexist");
            recordSet.setResourceRecords(Lists.newArrayList(record));

            resourceRecordSetOpt = Optional.of(recordSet);
        }

        ResourceRecordSet resourceRecordSet = resourceRecordSetOpt.get();
        ResourceRecord resourceRecord = getOnlyElement(resourceRecordSet.getResourceRecords());
        LOG.info("[{}] Found CNAME {} with current value {}", getCommand(), resourceRecordSet.getName(), resourceRecord.getValue());

        if (resourceRecord.getValue().equals(instanceAddr)) {
            LOG.info("[{}] Echo DB instance {} ({}) lines up with CNAME {}. Nothing to do.",
                    getCommand(), tagEchoManaged, instanceAddr, resourceRecordSet.getName());
        } else {
            LOG.info("[{}] Echo DB instance {} ({}) differs from CNAME {}.",
                    getCommand(), tagEchoManaged, instanceAddr, resourceRecordSet.getName());

            LOG.info("[{}] Updating CNAME {} from {} to {}", getCommand(), cfg.name(), resourceRecord.getValue(), instanceAddr);
            ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest()
                    .withHostedZoneId(hostedZone.getId())
                    .withChangeBatch(new ChangeBatch()
                            .withChanges(new Change(ChangeAction.UPSERT, new ResourceRecordSet(cfg.promoteCname(), RRType.CNAME)
                                    .withResourceRecords(new ResourceRecord(instanceAddr))
                                    .withTTL(cfg.promoteTtl()))));
            route53.changeResourceRecordSets(request);
        }

        if (cfg.interactive()) {
            String format = "Are you sure you want to promote %s to be the new target of %s? Input %s to confirm.";
            if (!EchoUtil.prompt(dbInstanceId, format, dbInstanceId, cfg.promoteCname(), dbInstanceId)) {
                LOG.info("User declined to proceed. Exiting.");
                return false;
            }
        }

        Optional<String[]> promoteTags = cfg.promoteTags();
        if (promoteTags.isPresent()) {
            List<Tag> tags = EchoUtil.parseTags(promoteTags.get());
            if (tags.size() > 0) {
                LOG.info("[{}] Applying tags on promote: {}", getCommand(), Arrays.asList(tags));
                AddTagsToResourceRequest tagsRequest = new AddTagsToResourceRequest()
                        .withResourceName(RdsFind.instanceArn(cfg.region(), cfg.accountNumber(),
                                instance.getDBInstanceIdentifier()));
                tagsRequest.setTags(tags);
                rds.addTagsToResource(tagsRequest);
            }
        }

        LOG.info("[{}] Searching for any existing promoted instance to demote.", getCommand()); // TODO no it doesn't

        EchoUtil echo = new EchoUtil();

        Iterable<DBInstance> validInstances = echo.echoInstances();
        String tagEchoStage = echo.getTagEchoStage();

        for (DBInstance validInstance : validInstances) {
            if (validInstance.getDBInstanceArn().equalsIgnoreCase(instance.getDBInstanceArn())) {
                continue;
            }

            rds.addTagsToResource(new AddTagsToResourceRequest()
                    .withResourceName(RdsFind.instanceArn(cfg.region(), cfg.accountNumber(), validInstance.getDBInstanceIdentifier()))
                    .withTags(new Tag().withKey(tagEchoStage).withValue(EchoConst.STAGE_FORGOTTEN)));
        }

        return true;
    }

    @Override
    String getCommand() {
        return EchoConst.COMMAND_PROMOTE;
    }

    public static void main(String[] args) throws Exception {
        new EchoPromote().call();
    }
}
