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

import com.amazonaws.services.rds.model.AddTagsToResourceRequest;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DeleteDBInstanceRequest;
import com.amazonaws.services.rds.model.Tag;
import com.github.blacklocus.rdsecho.utl.EchoUtil;
import com.github.blacklocus.rdsecho.utl.RdsFind;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoRetire extends AbstractEchoIntermediateStage {

    private static final Logger LOG = LoggerFactory.getLogger(EchoRetire.class);

    public EchoRetire() {
        super(EchoConst.STAGE_FORGOTTEN, EchoConst.STAGE_RETIRED);
    }

    @Override
    public Boolean call() throws Exception {

        // Validate state, make sure we're operating on what we expect to.

        String tagEchoManaged = echo.getTagEchoManaged();
        String tagEchoStage = echo.getTagEchoStage();
        String command = this.getCommand();

        LOG.info("[{}] Locating Echo managed instances (tagged with {}=true)", command, tagEchoManaged);

        Iterable<DBInstance> instances = echo.echoInstances();
        for (DBInstance instance : instances) {
            String dbInstanceId = instance.getDBInstanceIdentifier();
            LOG.info("[{}] Located echo-managed instance with identifier {}", command, dbInstanceId);

            Optional<Tag> stageOpt = echo.instanceStage(instance.getDBInstanceIdentifier());
            if (!stageOpt.isPresent()) {
                LOG.error("[{}] Unable to read Echo stage tag on instance {}. Exiting.\n" +
                                "(If the instance is supposed to be in stage {} but isn't, edit " +
                                "the instance's tags to add {}={} and run this operation again.)",
                        command, dbInstanceId, requisiteStage, tagEchoStage, requisiteStage);
                continue;
            }
            String instanceStage = stageOpt.get().getValue();
            if (!requisiteStage.equals(instanceStage)) {
                LOG.info("[{}] Instance {} has stage {} but this operation is looking for {}={}. Exiting.\n",
                        command, dbInstanceId, instanceStage, tagEchoStage, requisiteStage);
                continue;
            }

            // Looks like we found a good echo instance, but is it available to us?

            if (!"available".equals(instance.getDBInstanceStatus())) {
                LOG.info("[{}] Instance {} is in correct stage of {} but does not have status 'available' (saw {}) so aborting.",
                        command, dbInstanceId, instanceStage, instance.getDBInstanceStatus());
                continue;
            }

            // Do the part special to traversing this stage

            if (traverseStage(instance)) {
                // Advance. This replaces, same-named tags.
                rds.addTagsToResource(new AddTagsToResourceRequest()
                        .withResourceName(RdsFind.instanceArn(cfg.region(), cfg.accountNumber(), instance.getDBInstanceIdentifier()))
                        .withTags(new Tag().withKey(tagEchoStage).withValue(resultantStage)));
            }
        }

        return true;
    }


    @Override
    boolean traverseStage(DBInstance instance) {

        String dbInstanceId = instance.getDBInstanceIdentifier();
        LOG.info("[{}] Propose to retire (destroy) instance {}", getCommand(), dbInstanceId);

        if (cfg.interactive()) {
            String format = "Are you sure you want to retire this instance? Input %s to confirm.";
            if (!EchoUtil.prompt(dbInstanceId, format, dbInstanceId)) {
                LOG.info("User declined to proceed. Exiting.");
                return false;
            }
        }

        LOG.info("[{}] Retiring instance {}", getCommand(), dbInstanceId);
        DeleteDBInstanceRequest request = new DeleteDBInstanceRequest()
                .withDBInstanceIdentifier(dbInstanceId)
                .withSkipFinalSnapshot(cfg.retireSkipFinalSnapshot().orNull())
                .withFinalDBSnapshotIdentifier(cfg.retireFinalDbSnapshotIdentifier().orNull());
        rds.deleteDBInstance(request);
        LOG.info("[{}] So long {}", getCommand(), dbInstanceId);

        return true;
    }

    @Override
    String getCommand() {
        return EchoConst.COMMAND_RETIRE;
    }

    public static void main(String[] args) throws Exception {
        new EchoRetire().call();
    }
}
