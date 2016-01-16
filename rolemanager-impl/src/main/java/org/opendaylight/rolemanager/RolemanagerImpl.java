/*
 * Copyright (c) 2015 Sapienza University of Rome.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.rolemanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.openflowplugin.openflow.md.util.RoleUtil;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.GetRolemanagerStatusOutput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.GetRolemanagerStatusOutputBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.GetSwitchRoleInput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.GetSwitchRoleOutput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.GetSwitchRoleOutputBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.Rolemanager;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.Rolemanager.RolemanagerStatus;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.RolemanagerBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.RolemanagerService;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.SetSwitchRoleInput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.SetSwitchRoleOutput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.SetSwitchRoleOutputBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.StartRolemanagerInput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.StartRolemanagerOutput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.StartRolemanagerOutputBuilder;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.StopRolemanagerOutput;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.rolemanager.rev150901.StopRolemanagerOutputBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class RolemanagerImpl implements BindingAwareProvider,
        DataChangeListener,
        AutoCloseable,
        RolemanagerService{


    private static final Logger LOG = LoggerFactory.getLogger(RolemanagerImpl.class);
    private static String TAG = "Rolemanager";
    private ProviderContext providerContext;
    private DataBroker dataBroker;
    private ListenerRegistration<DataChangeListener> dcReg;
    private BindingAwareBroker.RpcRegistration<RolemanagerService> rpcReg;
    public static final InstanceIdentifier<Rolemanager> ROLEMANAGER_IID = InstanceIdentifier.builder(Rolemanager.class).build();
    //private static final String DEFAULT_TOPOLOGY_ID = "flow:1";



    @Override
    public void close() throws Exception {
        dcReg.close();
        rpcReg.close();
        LOG.info(TAG, "Registrations closed");
    }



    @Override
    public void onSessionInitiated(ProviderContext session) {
        this.providerContext = session;
        this.dataBroker = session.getSALService(DataBroker.class);
        dcReg = dataBroker.registerDataChangeListener(
                LogicalDatastoreType.CONFIGURATION, ROLEMANAGER_IID, this,
                DataChangeScope.SUBTREE);
        rpcReg = session.addRpcImplementation(RolemanagerService.class, this);
        initRolemanagerOperational();
        initRolemanagerConfiguration();
        LOG.info(TAG, "onSessionInitiated: initialization done");
    }




    @Override
    public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        DataObject dataObject = change.getUpdatedSubtree();
        if (dataObject instanceof Rolemanager) {
            Rolemanager rolemanager = (Rolemanager) dataObject;
            LOG.info(TAG, "onDataChanged - new Rolemanager config: {}",
                    rolemanager);
        } else {
            LOG.warn(TAG, "onDataChanged - not instance of Rolemanager {}",
                    dataObject);
        }
    }



    private void initRolemanagerOperational() {
        Rolemanager rolemanager = new RolemanagerBuilder().setRolemanagerStatus(RolemanagerStatus.Down).build();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, ROLEMANAGER_IID, rolemanager);
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.info("initRolemanagerOperational: transaction succeeded");
            }
            @Override
            public void onFailure(final Throwable t) {
                LOG.error("initRolemanagerOperational: transaction failed");
            }
        });
        LOG.info("initRolemanagerOperational: operational status populated: {}", rolemanager);
    }



    private void initRolemanagerConfiguration() {
        Rolemanager rolemanager = new RolemanagerBuilder()/*.setDarknessFactor((long) 1000)*/.build();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, ROLEMANAGER_IID, rolemanager);
        tx.submit();
        LOG.info("initRolemanagerConfiguration: default config populated: {}", rolemanager);
    }






    @Override
    public Future<RpcResult<StartRolemanagerOutput>> startRolemanager(StartRolemanagerInput input) {
        LOG.info(TAG, "Starting Rolemanager...");
        LOG.info(TAG, "Write rolemanager status in datastore");
        Rolemanager rolemanager = new RolemanagerBuilder().setRolemanagerStatus(RolemanagerStatus.Up).build();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, ROLEMANAGER_IID, rolemanager);
        //
        //TODO
        //
        //
        //
        StartRolemanagerOutputBuilder slbob = new StartRolemanagerOutputBuilder();
        slbob.setResponseCode(1L);
        slbob.setResponseMessage("Rolemanager started");
        LOG.info(TAG, "Rolemanager started!");
        return RpcResultBuilder.success(slbob.build()).buildFuture();
    }





    @Override
    public Future<RpcResult<GetRolemanagerStatusOutput>> getRolemanagerStatus() {
        LOG.info(TAG, "Get Rolemanager status started...");
        LOG.info(TAG, "Reading Rolemanager status");
        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        Optional<Rolemanager> rolemanager = null;
        GetRolemanagerStatusOutputBuilder glbsob = new GetRolemanagerStatusOutputBuilder();
        try {
            rolemanager = tx.read(LogicalDatastoreType.OPERATIONAL, ROLEMANAGER_IID).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error(TAG, "Error when retrieving the Rolemanager status");
            glbsob.setResponseCode(-1L);
        }
        if(rolemanager!=null && rolemanager.isPresent()){
            LOG.error(TAG, "Rolemanager status null or not present");
            long status = rolemanager.get().getRolemanagerStatus().getIntValue();
            glbsob.setResponseCode(status);
        }
        else
            glbsob.setResponseCode(-1L);
        LOG.info(TAG, "Returing Rolemanager status");
        return RpcResultBuilder.success(glbsob.build()).buildFuture();
    }






    @Override
    public Future<RpcResult<StopRolemanagerOutput>> stopRolemanager() {
        LOG.info(TAG, "Stopping Rolemanager...");
        Rolemanager rolemanager = new RolemanagerBuilder().setRolemanagerStatus(RolemanagerStatus.Down).build();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, ROLEMANAGER_IID, rolemanager);
        //
        //TODO
        //
        //
        //
        StopRolemanagerOutputBuilder slbob = new StopRolemanagerOutputBuilder();
        slbob.setResponseCode(1L);
        LOG.info(TAG, "Rolemanager stopped!");
        return RpcResultBuilder.success(slbob.build()).buildFuture();
    }




    @Override
    public Future<RpcResult<SetSwitchRoleOutput>> setSwitchRole(SetSwitchRoleInput input) {
        LOG.info(TAG, "Set switches role stated");
        int role = 0;
        try{
            role = Integer.parseInt(input.getOfpRole()+"");
        }catch(RuntimeException e){
            LOG.error(TAG, "Error while parsing the request's role");
            SetSwitchRoleOutputBuilder swrob = new SetSwitchRoleOutputBuilder();
            swrob.setResponseCode(-1L);
            swrob.setResponseMessage("Error while parsing the request's role");
            return RpcResultBuilder.success(swrob.build()).buildFuture();
        }
        /**
         * no change to role
         * NOCHANGE(0),
         * promote current role to MASTER
         * BECOMEMASTER(1)
         * demote current role to SLAVE
         * BECOMESLAVE(2)
         */
        if(role !=0 && input.getSwitchIds().size()>0){
            RoleUtil.fireRoleChange(role, input.getSwitchIds());
            SetSwitchRoleOutputBuilder swrob = new SetSwitchRoleOutputBuilder();
            swrob.setResponseCode(0L);
            swrob.setResponseMessage("Switch(es) role changed");
            return RpcResultBuilder.success(swrob.build()).buildFuture();
        }
        else{
            if(role==0){
                LOG.warn(TAG, "Requested role change is 0 (NOCHANGE role), nothing to do...");
                SetSwitchRoleOutputBuilder swrob = new SetSwitchRoleOutputBuilder();
                swrob.setResponseCode(0L);
                swrob.setResponseMessage("OK, NOCHANGE role");
                return RpcResultBuilder.success(swrob.build()).buildFuture();
            }
            else{
                LOG.warn(TAG, "Requested role change is empty dpIDs list, nothing to do...");
                SetSwitchRoleOutputBuilder swrob = new SetSwitchRoleOutputBuilder();
                swrob.setResponseCode(0L);
                swrob.setResponseMessage("OK, empty dpIDs list");
                return RpcResultBuilder.success(swrob.build()).buildFuture();
            }
        }
    }



    @Override
    public Future<RpcResult<GetSwitchRoleOutput>> getSwitchRole(GetSwitchRoleInput input) {
        LOG.info(TAG, "Getting switches roles started");
        List<String> dpRoles = new ArrayList<String>();
        Map<String, String> swsRoles = RoleUtil.getSwitchesRoles();
        if(swsRoles==null){
            LOG.error(TAG, "Error while retieving the switches roles");
            GetSwitchRoleOutputBuilder gsrob = new GetSwitchRoleOutputBuilder();
            gsrob.setResponseCode(-1L);
            gsrob.setResponseMessage(new ArrayList<String>());
            return RpcResultBuilder.success(gsrob.build()).buildFuture();
        }
        for(String r : swsRoles.keySet()){
            //dpRoles.add(r.toString()+":"+getRoleIntValue(swsRoles.get(r)));
            dpRoles.add(r.toString()+":"+swsRoles.get(r));
        }
        GetSwitchRoleOutputBuilder gsrob = new GetSwitchRoleOutputBuilder();
        gsrob.setResponseCode(0L);
        gsrob.setResponseMessage(dpRoles);
        return RpcResultBuilder.success(gsrob.build()).buildFuture();
    }


}
