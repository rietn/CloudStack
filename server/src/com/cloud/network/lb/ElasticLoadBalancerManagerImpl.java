/**
 *  Copyright (C) 2011 Citrix Systems, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.cloud.network.lb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.AgentManager.OnError;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.LoadBalancerConfigCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.to.LoadBalancerTO;
import com.cloud.agent.manager.Commands;
import com.cloud.api.commands.CreateLoadBalancerRuleCmd;
import com.cloud.configuration.Config;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.DataCenter;
import com.cloud.dc.Pod;
import com.cloud.dc.PodVlanMapVO;
import com.cloud.dc.Vlan.VlanType;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.dc.dao.PodVlanMapDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.deploy.DataCenterDeployment;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.network.ElasticLbVmMapVO;
import com.cloud.network.IPAddressVO;
import com.cloud.network.LoadBalancerVO;
import com.cloud.network.Network;
import com.cloud.network.Network.GuestIpType;
import com.cloud.network.NetworkManager;
import com.cloud.network.NetworkVO;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.lb.LoadBalancingRule.LbDestination;
import com.cloud.network.lb.dao.ElasticLbVmMapDao;
import com.cloud.network.router.VirtualNetworkApplianceManager;
import com.cloud.network.router.VirtualRouter;
import com.cloud.network.router.VirtualRouter.Role;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRule.Purpose;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.network.security.SecurityGroupManagerImpl.WorkerThread;
import com.cloud.offering.NetworkOffering;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.User;
import com.cloud.user.UserContext;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Inject;
import com.cloud.utils.component.Manager;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.DomainRouterVO;
import com.cloud.vm.NicProfile;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineName;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfile.Param;
import com.cloud.vm.dao.DomainRouterDao;

@Local(value = { ElasticLoadBalancerManager.class })
public class ElasticLoadBalancerManagerImpl implements
        ElasticLoadBalancerManager, Manager {
    private static final Logger s_logger = Logger
            .getLogger(ElasticLoadBalancerManagerImpl.class);
    
    @Inject
    IPAddressDao _ipAddressDao;
    @Inject
    AgentManager _agentMgr;
    @Inject
    NetworkManager _networkMgr;
    @Inject
    LoadBalancerDao _loadBalancerDao = null;
    @Inject
    LoadBalancingRulesManager _lbMgr;
    @Inject
    VirtualNetworkApplianceManager _routerMgr;
    @Inject
    DomainRouterDao _routerDao = null;
    @Inject
    protected HostPodDao _podDao = null;
    @Inject
    protected ClusterDao _clusterDao;
    @Inject
    DataCenterDao _dcDao = null;
    @Inject
    protected NetworkDao _networkDao;
    @Inject
    protected NetworkOfferingDao _networkOfferingDao;
    @Inject
    VMTemplateDao _templateDao = null;
    @Inject
    VirtualMachineManager _itMgr;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    ServiceOfferingDao _serviceOfferingDao = null;
    @Inject
    AccountService _accountService;
    @Inject
    LoadBalancerDao _lbDao;
    @Inject
    VlanDao _vlanDao;
    @Inject
    PodVlanMapDao _podVlanMapDao;
    @Inject
    ElasticLbVmMapDao _elbVmMapDao;
    @Inject 
    NetworkDao _networksDao;
    @Inject 
    AccountDao _accountDao;


    String _name;
    String _instance;
    
    boolean _enabled;
    TrafficType _frontendTrafficType = TrafficType.Guest;

    Account _systemAcct;
    ServiceOfferingVO _elasticLbVmOffering;
    ScheduledExecutorService _gcThreadPool;
    
    int _elasticLbVmRamSize;
    int _elasticLbvmCpuMHz;
    
    private Long getPodIdForDirectIp(IPAddressVO ipAddr) {
        List<PodVlanMapVO> podVlanMaps = _podVlanMapDao.listPodVlanMapsByVlan(ipAddr.getVlanId());
        if (podVlanMaps.isEmpty()) {
            return null;
        } else {
            return podVlanMaps.get(0).getPodId();
        }
    }


    public DomainRouterVO deployLoadBalancerVM(Long networkId, IPAddressVO ipAddr, Long accountId) {  
        NetworkVO network = _networkDao.findById(networkId);
        DataCenter dc = _dcDao.findById(network.getDataCenterId());
        Long podId = getPodIdForDirectIp(ipAddr);
        Pod pod = podId == null?null:_podDao.findById(podId);
        Map<VirtualMachineProfile.Param, Object> params = new HashMap<VirtualMachineProfile.Param, Object>(
                1);
        params.put(VirtualMachineProfile.Param.RestartNetwork, true);
        Account owner = _accountService.getActiveAccount("system", new Long(1));
        DeployDestination dest = new DeployDestination(dc, pod, null, null);
        s_logger.debug("About to deploy elastic LB vm ");

        try {
            DomainRouterVO elbVm = deployELBVm(network, dest, owner, params);

            s_logger.debug("ELB  vm = " + elbVm);
            if (elbVm == null) {
                throw new InvalidParameterValueException("Could not deploy or find existing ELB VM");
            }
            return elbVm;
           
        } catch (Throwable t) {
            String errorMsg = "Error while deploying Loadbalancer VM:  " + t;
            s_logger.warn(errorMsg);
            return null;
        }

    }
    
    private boolean sendCommandsToRouter(final DomainRouterVO router,
            Commands cmds) throws AgentUnavailableException {
        Answer[] answers = null;
        try {
            answers = _agentMgr.send(router.getHostId(), cmds);
        } catch (OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
            throw new AgentUnavailableException(
                    "Unable to send commands to virtual router ",
                    router.getHostId(), e);
        }

        if (answers == null) {
            return false;
        }

        if (answers.length != cmds.size()) {
            return false;
        }

        // FIXME: Have to return state for individual command in the future
        if (answers.length > 0) {
            Answer ans = answers[0];
            return ans.getResult();
        }
        return true;
    }

    private void createApplyLoadBalancingRulesCommands(
            List<LoadBalancingRule> rules, DomainRouterVO router, Commands cmds) {


        LoadBalancerTO[] lbs = new LoadBalancerTO[rules.size()];
        int i = 0;
        for (LoadBalancingRule rule : rules) {
            boolean revoked = (rule.getState()
                    .equals(FirewallRule.State.Revoke));
            String protocol = rule.getProtocol();
            String algorithm = rule.getAlgorithm();

            String elbIp = _networkMgr.getIp(rule.getSourceIpAddressId()).getAddress()
                    .addr();
            int srcPort = rule.getSourcePortStart();
            List<LbDestination> destinations = rule.getDestinations();
            LoadBalancerTO lb = new LoadBalancerTO(elbIp, srcPort, protocol,
                    algorithm, revoked, false, destinations);
            lbs[i++] = lb;
        }

        LoadBalancerConfigCommand cmd = new LoadBalancerConfigCommand(lbs);
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP,
                router.getPrivateIpAddress());
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME,
                router.getInstanceName());
        cmds.addCommand(cmd);

    }

    protected boolean applyLBRules(DomainRouterVO router,
            List<LoadBalancingRule> rules) throws ResourceUnavailableException {
        Commands cmds = new Commands(OnError.Continue);
        createApplyLoadBalancingRulesCommands(rules, router, cmds);
        // Send commands to router
        return sendCommandsToRouter(router, cmds);
    }
    
    protected DomainRouterVO findElbVmForLb(FirewallRule lb) {//TODO: use a table to lookup
        ElasticLbVmMapVO map = _elbVmMapDao.findOneByIp(lb.getSourceIpAddressId());
        if (map == null) {
            return null;
        }
        DomainRouterVO elbVm = _routerDao.findById(map.getElbVmId());
        return elbVm;
    }

    public boolean applyLoadBalancerRules(Network network,
            List<? extends FirewallRule> rules)
            throws ResourceUnavailableException {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        if (rules.get(0).getPurpose() != Purpose.LoadBalancing) {
            s_logger.warn("Not handling non-LB firewall rules");
            return false;
        }
        
        DomainRouterVO elbVm = findElbVmForLb(rules.get(0));
                                                                          
        if (elbVm == null) {
            s_logger.warn("Unable to apply lb rules, ELB vm  doesn't exist in the network "
                    + network.getId());
            throw new ResourceUnavailableException("Unable to apply lb rules",
                    DataCenter.class, network.getDataCenterId());
        }

        if (elbVm.getState() == State.Running) {
            //resend all rules for the public ip
            List<LoadBalancerVO> lbs = _lbDao.listByIpAddress(rules.get(0).getSourceIpAddressId());
            List<LoadBalancingRule> lbRules = new ArrayList<LoadBalancingRule>();
            for (LoadBalancerVO lb : lbs) {
                List<LbDestination> dstList = _lbMgr.getExistingDestinations(lb.getId());
                LoadBalancingRule loadBalancing = new LoadBalancingRule(
                        lb, dstList);
                lbRules.add(loadBalancing); 
            }
            return applyLBRules(elbVm, lbRules);
        } else if (elbVm.getState() == State.Stopped
                || elbVm.getState() == State.Stopping) {
            s_logger.debug("ELB VM is in "
                    + elbVm.getState()
                    + ", so not sending apply LoadBalancing rules commands to the backend");
            return true;
        } else {
            s_logger.warn("Unable to apply loadbalancing rules, ELB VM is not in the right state "
                    + elbVm.getState());
            throw new ResourceUnavailableException(
                    "Unable to apply loadbalancing rules, ELB VM is not in the right state",
                    VirtualRouter.class, elbVm.getId());
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params)
            throws ConfigurationException {
        _name = name;
        final Map<String, String> configs = _configDao.getConfiguration("AgentManager", params);
        _systemAcct = _accountService.getSystemAccount();
        _instance = configs.get("instance.name");
        if (_instance == null) {
            _instance = "VM";
        }
        boolean useLocalStorage = Boolean.parseBoolean(configs.get(Config.SystemVMUseLocalStorage.key()));

        _elasticLbVmRamSize = NumbersUtil.parseInt(configs.get("elastic.lb.vm.ram.size"), DEFAULT_ELB_VM_RAMSIZE);
        _elasticLbvmCpuMHz = NumbersUtil.parseInt(configs.get("elastic.lb.vm.cpu.mhz"), DEFAULT_ELB_VM_CPU_MHZ);
        _elasticLbVmOffering = new ServiceOfferingVO("System Offering For Elastic LB VM", 1, _elasticLbVmRamSize, _elasticLbvmCpuMHz, 0, 0, true, null, useLocalStorage, true, null, true, VirtualMachine.Type.ElasticLoadBalancerVm, true);
        _elasticLbVmOffering.setUniqueName("Cloud.Com-ElasticLBVm");
        _elasticLbVmOffering = _serviceOfferingDao.persistSystemServiceOffering(_elasticLbVmOffering);
        
        String enabled = _configDao.getValue(Config.ElasticLoadBalancerEnabled.key());
        _enabled = (enabled == null) ? false: Boolean.parseBoolean(enabled);
        if (_enabled) {
            String traffType = _configDao.getValue(Config.ElasticLoadBalancerNetwork.key());
            if ("guest".equalsIgnoreCase(traffType)) {
                _frontendTrafficType = TrafficType.Guest;
            } else if ("public".equalsIgnoreCase(traffType)){
                _frontendTrafficType = TrafficType.Public;
            } else
                throw new ConfigurationException("Traffic type for front end of load balancer has to be guest or public; found : " + traffType);
            _gcThreadPool = Executors.newScheduledThreadPool(1, new NamedThreadFactory("ELBVM-GC"));
            _gcThreadPool.scheduleAtFixedRate(new CleanupThread(), 30, 30, TimeUnit.SECONDS);
        }
        

        return true;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    private DomainRouterVO findELBVmWithCapacity(Network guestNetwork, IPAddressVO ipAddr) {
        List<DomainRouterVO> elbVms = _routerDao.listByNetworkAndRole(guestNetwork.getId(), Role.LB);
        return null;
    }
    
    public DomainRouterVO deployELBVm(Network guestNetwork, DeployDestination dest, Account owner, Map<Param, Object> params) throws
                ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        long dcId = dest.getDataCenter().getId();

        // lock guest network
        Long guestNetworkId = guestNetwork.getId();
        guestNetwork = _networkDao.acquireInLockTable(guestNetworkId);

        if (guestNetwork == null) {
            throw new ConcurrentOperationException("Unable to acquire network configuration: " + guestNetworkId);
        }

        try {

            NetworkOffering offering = _networkOfferingDao.findByIdIncludingRemoved(guestNetwork.getNetworkOfferingId());
            if (offering.isSystemOnly() || guestNetwork.getIsShared()) {
                owner = _accountService.getSystemAccount();
            }

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Starting a elastic ip vm for network configurations: " + guestNetwork + " in " + dest);
            }
            assert guestNetwork.getState() == Network.State.Implemented 
                || guestNetwork.getState() == Network.State.Setup 
                || guestNetwork.getState() == Network.State.Implementing 
                : "Network is not yet fully implemented: "+ guestNetwork;

            DataCenterDeployment plan = null;
            DomainRouterVO elbVm = null;
            
            plan = new DataCenterDeployment(dcId, dest.getPod().getId(), null, null, null);

            if (elbVm == null) {
                long id = _routerDao.getNextInSequence(Long.class, "id");
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Creating the elastic LB vm " + id);
                }
 
                List<NetworkOfferingVO> offerings = _networkMgr.getSystemAccountNetworkOfferings(NetworkOfferingVO.SystemControlNetwork);
                NetworkOfferingVO controlOffering = offerings.get(0);
                NetworkVO controlConfig = _networkMgr.setupNetwork(_systemAcct, controlOffering, plan, null, null, false, false).get(0);

                List<Pair<NetworkVO, NicProfile>> networks = new ArrayList<Pair<NetworkVO, NicProfile>>(2);
                NicProfile guestNic = new NicProfile();
                networks.add(new Pair<NetworkVO, NicProfile>((NetworkVO) guestNetwork, guestNic));
                networks.add(new Pair<NetworkVO, NicProfile>(controlConfig, null));
                
                VMTemplateVO template = _templateDao.findSystemVMTemplate(dcId);

               
                elbVm = new DomainRouterVO(id, _elasticLbVmOffering.getId(), VirtualMachineName.getRouterName(id, _instance), template.getId(), template.getHypervisorType(), template.getGuestOSId(),
                        owner.getDomainId(), owner.getId(), guestNetwork.getId(), _elasticLbVmOffering.getOfferHA());
                elbVm.setRole(Role.LB);
                elbVm = _itMgr.allocate(elbVm, template, _elasticLbVmOffering, networks, plan, null, owner);
            }

            State state = elbVm.getState();
            if (state != State.Running) {
                elbVm = this.start(elbVm, _accountService.getSystemUser(), _accountService.getSystemAccount(), params);
            }


            return elbVm;
        } finally {
            _networkDao.releaseFromLockTable(guestNetworkId);
        }
    }
    
    private DomainRouterVO start(DomainRouterVO router, User user, Account caller, Map<Param, Object> params) throws StorageUnavailableException, InsufficientCapacityException,
    ConcurrentOperationException, ResourceUnavailableException {
        s_logger.debug("Starting router " + router);
        if (_itMgr.start(router, params, user, caller) != null) {
            return _routerDao.findById(router.getId());
        } else {
            return null;
        }
    }
    
    
    private DomainRouterVO stop(DomainRouterVO elbVm, boolean forced, User user, Account caller) throws ConcurrentOperationException, ResourceUnavailableException {
        s_logger.debug("Stopping elb vm " + elbVm);
        try {
            if (_itMgr.advanceStop( elbVm, forced, user, caller)) {
                return _routerDao.findById(elbVm.getId());
            } else {
                return null;
            }
        } catch (OperationTimedoutException e) {
            throw new CloudRuntimeException("Unable to stop " + elbVm, e);
        }
    }
    
    protected List<LoadBalancerVO> findExistingLoadBalancers(String lbName, Long ipId, Long accountId, Long domainId, Integer publicPort) {
        SearchBuilder<LoadBalancerVO> sb = _lbDao.createSearchBuilder();
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.EQ); 
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("publicPort", sb.entity().getSourcePortStart(), SearchCriteria.Op.EQ);
        if (ipId != null) {
            sb.and("sourceIpAddress", sb.entity().getSourceIpAddressId(), SearchCriteria.Op.EQ);
        }
        if (domainId != null) {
            sb.and("domainId", sb.entity().getDomainId(), SearchCriteria.Op.EQ);
        }
        if (publicPort != null) {
            sb.and("publicPort", sb.entity().getSourcePortStart(), SearchCriteria.Op.EQ);
        }
        SearchCriteria<LoadBalancerVO> sc = sb.create();
        sc.setParameters("name", lbName);
        sc.setParameters("accountId", accountId);
        if (ipId != null) {
            sc.setParameters("sourceIpAddress", ipId);
        } 
        if (domainId != null) {
            sc.setParameters("domainId",domainId);
        }
        if (publicPort != null) {
            sc.setParameters("publicPort", publicPort);
        }
        List<LoadBalancerVO> lbs = _lbDao.search(sc, null);
   
        return lbs == null || lbs.size()==0 ? null: lbs;
    }
    
    @DB
    public PublicIp allocIp(CreateLoadBalancerRuleCmd lb, Account account) throws InsufficientAddressCapacityException {
        //TODO: this only works in the guest network. Handle the public network case also.
        List<NetworkOfferingVO> offerings = _networkOfferingDao.listByTrafficTypeAndGuestType(true, _frontendTrafficType, GuestIpType.Direct);
        if (offerings == null || offerings.size() == 0) {
            s_logger.warn("Could not find system offering for direct networks of type " + _frontendTrafficType);
            return null;
        }
        NetworkOffering frontEndOffering = offerings.get(0);
        List<NetworkVO> networks = _networksDao.listBy(Account.ACCOUNT_ID_SYSTEM, frontEndOffering.getId(), lb.getZoneId());
        if (networks == null || networks.size() == 0) {
            s_logger.warn("Could not find network of offering type " + frontEndOffering +  " in zone " + lb.getZoneId());
            return null;
        }
        Network frontEndNetwork = networks.get(0);
        Transaction txn = Transaction.currentTxn();
        txn.start();
        PublicIp ip = _networkMgr.assignPublicIpAddress(lb.getZoneId(), null, account, VlanType.DirectAttached, frontEndNetwork.getId(), null);
        IPAddressVO ipvo = _ipAddressDao.findById(ip.getId());
        ipvo.setAssociatedWithNetworkId(frontEndNetwork.getId()); 
        _ipAddressDao.update(ipvo.getId(), ipvo);
        txn.commit();
        s_logger.info("Acquired public IP for loadbalancing " + ip);

        return ip;
    }
    
    public void releaseIp(long ipId, long userId, Account caller) {
        s_logger.info("Release public IP for loadbalancing " + ipId);
       _networkMgr.releasePublicIpAddress(ipId, userId, caller);
    }

    @Override
    @DB
    public LoadBalancer handleCreateLoadBalancerRule( CreateLoadBalancerRuleCmd lb, Account account) throws InsufficientAddressCapacityException, NetworkRuleConflictException  {
        Long ipId = lb.getSourceIpAddressId();
        boolean newIp = false;
        account = _accountDao.acquireInLockTable(account.getId());
        if (account == null) {
            s_logger.warn("CreateLoadBalancer: Failed to acquire lock on account");
        }
        try {
            List<LoadBalancerVO> existingLbs = findExistingLoadBalancers(lb.getName(), lb.getSourceIpAddressId(), lb.getAccountId(), lb.getDomainId(), lb.getSourcePortStart());
            if (existingLbs == null ){
                existingLbs = findExistingLoadBalancers(lb.getName(), lb.getSourceIpAddressId(), lb.getAccountId(), lb.getDomainId(), null);
                if (existingLbs == null) {
                    if (lb.getSourceIpAddressId() != null) {
                        existingLbs = findExistingLoadBalancers(lb.getName(), null, lb.getAccountId(), lb.getDomainId(), null);
                        if (existingLbs != null) {
                            throw new InvalidParameterValueException("Supplied LB name " + lb.getName() + " is not associated with IP " + lb.getSourceIpAddressId() );
                        } 
                    } else {
                        PublicIp ip = allocIp(lb, account);
                        ipId = ip.getId();
                        newIp = true;
                    }
                } else {
                    ipId = existingLbs.get(0).getSourceIpAddressId();
                }
            } else {
                s_logger.warn("Found existing load balancers matching requested new LB");
                throw new NetworkRuleConflictException("Found existing load balancers matching requested new LB");
            }

            IPAddressVO ipAddr = _ipAddressDao.findById(ipId);
            Long networkId= ipAddr.getSourceNetworkId();
            NetworkVO network=_networkDao.findById(networkId);


            if (network.getGuestType() != GuestIpType.Direct) {
                s_logger.info("Elastic LB Manager: not handling guest traffic of type " + network.getGuestType());
                return null;
            }
            LoadBalancer result = null;
            try {
                lb.setSourceIpAddressId(ipId);
                result = _lbMgr.createLoadBalancer(lb);
            } catch (NetworkRuleConflictException e) {
                s_logger.warn("Failed to create LB rule, not continuing with ELB deployment");
                if (newIp) {
                    releaseIp(ipId, UserContext.current().getCallerUserId(), account);
                }
                throw e;
            }

            DomainRouterVO elbVm = null;


            if (existingLbs == null) {
                elbVm = findELBVmWithCapacity(network, ipAddr);
                if (elbVm == null) {
                    elbVm = deployLoadBalancerVM(networkId, ipAddr, account.getId());
                    if (elbVm == null) {
                        s_logger.warn("Failed to deploy a new ELB vm for ip " + ipAddr + " in network " + network + "lb name=" + lb.getName());
                        if (newIp)
                            releaseIp(ipId, UserContext.current().getCallerUserId(), account);
                       
                    }
                }

            } else {
                ElasticLbVmMapVO elbVmMap = _elbVmMapDao.findOneByIp(ipId);
                if (elbVmMap != null) {
                    elbVm = _routerDao.findById(elbVmMap.getElbVmId());
                }
            }
            
            if (elbVm == null) {
                s_logger.warn("No ELB VM can be found or deployed");
                s_logger.warn("Deleting LB since we failed to deploy ELB VM");
                _lbDao.remove(result.getId());
                return null;
            }
            
            ElasticLbVmMapVO mapping = new ElasticLbVmMapVO(ipId, elbVm.getId(), result.getId());
            _elbVmMapDao.persist(mapping);
            return result;
            
        } finally {
            if (account != null) {
                _accountDao.releaseFromLockTable(account.getId());
            }
        }
        
    }
    
    void garbageCollectUnusedElbVms() {
        List<DomainRouterVO> unusedElbVms = _elbVmMapDao.listUnusedElbVms();
        if (unusedElbVms != null && unusedElbVms.size() > 0)
            s_logger.info("Found " + unusedElbVms.size() + " unused ELB vms");
        else
            return;
        User user = _accountService.getSystemUser();
        for (DomainRouterVO elbVm : unusedElbVms) {
            try {
                s_logger.info("Attempting to stop ELB VM: " + elbVm);
                stop(elbVm, true, user, _systemAcct);
            } catch (ConcurrentOperationException e) {
                s_logger.warn("Unable to stop unused elb vm " + elbVm + " due to ", e);
                continue;
            } catch (ResourceUnavailableException e) {
                s_logger.warn("Unable to stop unused elb vm " + elbVm + " due to ", e);
                continue;
            }
            try {
                s_logger.info("Attempting to destroy ELB VM: " + elbVm);
                _itMgr.expunge(elbVm, user, _systemAcct);
            } catch (ResourceUnavailableException e) {
                s_logger.warn("Unable to destroy unused elb vm " + elbVm + " due to ", e);
            }
        }
    }
    
    public class CleanupThread implements Runnable {
        @Override
        public void run() {
            garbageCollectUnusedElbVms();
            
        }

        CleanupThread() {

        }
    }
}