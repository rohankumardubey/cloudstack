package com.cloud.baremetal.networkservice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.cloud.baremetal.database.BaremetalDhcpVO;
import com.cloud.baremetal.database.BaremetalPxeVO;
import com.cloud.dc.Pod;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.network.Network;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.GuestType;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.element.NetworkElement;
import com.cloud.offering.NetworkOffering;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.component.Inject;
import com.cloud.utils.db.SearchCriteria2;
import com.cloud.utils.db.SearchCriteriaService;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachine.Type;
import com.cloud.vm.dao.VMInstanceDao;

@Local(value = NetworkElement.class)
public class BaremetalPxeElement extends AdapterBase implements NetworkElement {
    private static final Logger s_logger = Logger.getLogger(BaremetalPxeElement.class);
    private static final Map<Service, Map<Capability, String>> capabilities;
    
    @Inject BaremetalPxeManager _pxeMgr;;
    @Inject VMInstanceDao _vmDao;
    
    static {
        Capability cap = new Capability(BaremetalPxeManager.BAREMETAL_PXE_CAPABILITY);
        Map<Capability, String> baremetalCaps = new HashMap<Capability, String>();
        baremetalCaps.put(cap, null);
        capabilities = new HashMap<Service, Map<Capability, String>>();
        capabilities.put(BaremetalPxeManager.BAREMETAL_PXE_SERVICE, baremetalCaps);
    }
    
    @Override
    public Map<Service, Map<Capability, String>> getCapabilities() {
        return capabilities;
    }

    @Override
    public Provider getProvider() {
    	return BaremetalPxeManager.BAREMETAL_PXE_SERVICE_PROVIDER;
    }

    private boolean canHandle(DeployDestination dest, TrafficType trafficType, GuestType networkType) {
        Pod pod = dest.getPod();
        if (pod != null && dest.getDataCenter().getNetworkType() == NetworkType.Basic && trafficType == TrafficType.Guest) {
            SearchCriteriaService<BaremetalPxeVO, BaremetalPxeVO> sc = SearchCriteria2.create(BaremetalPxeVO.class);
            sc.addAnd(sc.getEntity().getPodId(), Op.EQ, pod.getId());
            return sc.find() != null;
        }
        
        return false;
    }
    
    @Override
    public boolean implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        if (offering.isSystemOnly() || !canHandle(dest, offering.getTrafficType(), network.getGuestType())) {
            s_logger.debug("BaremetalPxeElement can not handle network offering: " + offering.getName());
            return false;
        }
        return true;
    }

    @Override
    public boolean prepare(Network network, NicProfile nic, VirtualMachineProfile<? extends VirtualMachine> vm, DeployDestination dest,
            ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        if (vm.getType() != Type.User || vm.getHypervisorType() != HypervisorType.BareMetal) {
            return false;
        }
        
        VMInstanceVO vo = _vmDao.findById(vm.getId());
        if (vo.getState() == null) {
        	/*This vm is just being created */
        	_pxeMgr.prepare(vm, dest, context);
        }
        
        return false;
    }

    @Override
    public boolean release(Network network, NicProfile nic, VirtualMachineProfile<? extends VirtualMachine> vm, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean destroy(Network network) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {
        return true;
    }

    @Override
    public boolean shutdownProviderInstances(PhysicalNetworkServiceProvider provider, ReservationContext context) throws ConcurrentOperationException,
            ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return false;
    }

    @Override
    public boolean verifyServicesCombination(List<String> services) {
        return true;
    }
}
