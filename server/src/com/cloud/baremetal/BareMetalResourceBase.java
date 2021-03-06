/**
 * *  Copyright (C) 2011 Citrix Systems, Inc.  All rights reserved
*
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

package com.cloud.baremetal;

import java.util.HashMap;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.MigrateAnswer;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingRoutingCommand;
import com.cloud.agent.api.PrepareForMigrationAnswer;
import com.cloud.agent.api.PrepareForMigrationCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.RebootAnswer;
import com.cloud.agent.api.RebootCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.StopAnswer;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.baremetal.IpmISetBootDevCommand;
import com.cloud.agent.api.baremetal.IpmISetBootDevCommand.BootDev;
import com.cloud.agent.api.baremetal.IpmiBootorResetCommand;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.api.ApiConstants;
import com.cloud.host.Host.Type;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.kvm.resource.KvmDummyResourceBase;
import com.cloud.hypervisor.xen.resource.CitrixResourceBase;
import com.cloud.resource.ServerResource;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;

@Local(value = ServerResource.class)
public class BareMetalResourceBase implements ServerResource {
	private static final Logger s_logger = Logger.getLogger(BareMetalResourceBase.class);
    protected HashMap<String, State> _vms = new HashMap<String, State>(2);
	protected String _name;
	protected String _uuid;
	protected String _zone;
    protected String _pod;
    protected String _cluster;
    protected long _memCapacity;
    protected long _cpuCapacity;
    protected long _cpuNum;
    protected String _mac;
    protected String _username;
    protected String _password;
    protected String _ip;
    protected IAgentControl _agentControl;
    protected Script _pingCommand;
    protected Script _setPxeBootCommand;
    protected Script _setDiskBootCommand;
    protected Script _rebootCommand;
    protected Script _getStatusCommand;
    protected Script _powerOnCommand;
    protected Script _powerOffCommand;
    protected Script _forcePowerOffCommand;
    protected Script _bootOrRebootCommand;
    protected String _vmName;

    private void changeVmState(String vmName, VirtualMachine.State state) {
        synchronized (_vms) {
            _vms.put(vmName, state);
        }
    }
    
    private State removeVmState(String vmName) {
        synchronized (_vms) {
            return _vms.remove(vmName);
        }
    }
    
	@Override
	public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
		_name = name;
		_uuid = (String) params.get("guid");
		try {
			_memCapacity = Long.parseLong((String)params.get(ApiConstants.MEMORY)) * 1024L * 1024L;
			_cpuCapacity = Long.parseLong((String)params.get(ApiConstants.CPU_SPEED));
			_cpuNum = Long.parseLong((String)params.get(ApiConstants.CPU_NUMBER));
		} catch (NumberFormatException e) {
			throw new ConfigurationException(String.format("Unable to parse number of CPU or memory capacity " +
					"or cpu capacity(cpu number = %1$s memCapacity=%2$s, cpuCapacity=%3$s", (String)params.get(ApiConstants.CPU_NUMBER),
					(String)params.get(ApiConstants.MEMORY), (String)params.get(ApiConstants.CPU_SPEED)));
		}
		
		_zone = (String) params.get("zone");
		_pod = (String) params.get("pod");
		_cluster = (String) params.get("cluster");
		_ip = (String)params.get("agentIp");
		_mac = (String)params.get(ApiConstants.HOST_MAC);
		_username = (String)params.get("username");
		_password = (String)params.get("password");
		_vmName = (String)params.get("vmName");
		
		if (_pod == null) {
            throw new ConfigurationException("Unable to get the pod");
        }

		if (_cluster == null) {
            throw new ConfigurationException("Unable to get the pod");
        }
		
        if (_ip == null) {
            throw new ConfigurationException("Unable to get the host address");
        }
        
        if (_mac.equalsIgnoreCase("unknown")) {
            throw new ConfigurationException("Unable to get the host mac address");
        }
        
        if (_mac.split(":").length != 6) {
            throw new ConfigurationException("Wrong MAC format(" + _mac + "). It must be in format of for example 00:11:ba:33:aa:dd which is not case sensitive");
        }
        
        if (_uuid == null) {
            throw new ConfigurationException("Unable to get the uuid");
        }
		
        String injectScript = "scripts/util/ipmi.py";
		String scriptPath = Script.findScript("", injectScript);
		if (scriptPath == null) {
			 throw new ConfigurationException("Cannot find ping script " + scriptPath);
		}
		_pingCommand = new Script(scriptPath, s_logger);
		_pingCommand.add("ping");
		_pingCommand.add("hostname="+_ip);
		_pingCommand.add("usrname="+_username);
		_pingCommand.add("password="+_password);
		
		_setPxeBootCommand = new Script(scriptPath, s_logger);
		_setPxeBootCommand.add("boot_dev");
		_setPxeBootCommand.add("hostname="+_ip);
		_setPxeBootCommand.add("usrname="+_username);
		_setPxeBootCommand.add("password="+_password);
		_setPxeBootCommand.add("dev=pxe");
		
		_setDiskBootCommand = new Script(scriptPath, s_logger);
		_setDiskBootCommand.add("boot_dev");
		_setDiskBootCommand.add("hostname="+_ip);
		_setDiskBootCommand.add("usrname="+_username);
		_setDiskBootCommand.add("password="+_password);
		_setDiskBootCommand.add("dev=disk");
		
		_rebootCommand = new Script(scriptPath, s_logger);
		_rebootCommand.add("reboot");
		_rebootCommand.add("hostname="+_ip);
		_rebootCommand.add("usrname="+_username);
		_rebootCommand.add("password="+_password);
		
		_getStatusCommand = new Script(scriptPath, s_logger);
		_getStatusCommand.add("ping");
		_getStatusCommand.add("hostname="+_ip);
		_getStatusCommand.add("usrname="+_username);
		_getStatusCommand.add("password="+_password);
		
		_powerOnCommand = new Script(scriptPath, s_logger);
		_powerOnCommand.add("power");
		_powerOnCommand.add("hostname="+_ip);
		_powerOnCommand.add("usrname="+_username);
		_powerOnCommand.add("password="+_password);
		_powerOnCommand.add("action=on");
		
		_powerOffCommand = new Script(scriptPath, s_logger);
		_powerOffCommand.add("power");
		_powerOffCommand.add("hostname="+_ip);
		_powerOffCommand.add("usrname="+_username);
		_powerOffCommand.add("password="+_password);
		_powerOffCommand.add("action=soft");
		
        _forcePowerOffCommand = new Script(scriptPath, s_logger);
        _forcePowerOffCommand.add("power");
        _forcePowerOffCommand.add("hostname=" + _ip);
        _forcePowerOffCommand.add("usrname=" + _username);
        _forcePowerOffCommand.add("password=" + _password);
        _forcePowerOffCommand.add("action=off");

		_bootOrRebootCommand = new Script(scriptPath, s_logger);
		_bootOrRebootCommand.add("boot_or_reboot");
		_bootOrRebootCommand.add("hostname="+_ip);
		_bootOrRebootCommand.add("usrname="+_username);
		_bootOrRebootCommand.add("password="+_password);
		
		return true;
	}

	protected boolean doScript(Script cmd) {
		return doScript(cmd, null);
	}
	
	protected boolean doScript(Script cmd, OutputInterpreter interpreter) {
		int retry = 5;
		String res = null;
		while (retry-- > 0) {
			if (interpreter == null) {
			res = cmd.execute();
			} else {
				res = cmd.execute(interpreter);
			}
			if (res != null && res.startsWith("Error: Unable to establish LAN")) {
				s_logger.warn("IPMI script timeout(" + cmd.toString() + "), will retry " + retry + " times");
				continue;
			} else if (res == null) {
				return true;
			} else {
				break;
			}
		}
		
		s_logger.warn("IPMI Scirpt failed due to " + res + "(" + cmd.toString() +")");
		return false;
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

	@Override
	public Type getType() {
		return com.cloud.host.Host.Type.Routing;
	}

	protected State getVmState() {
        OutputInterpreter.AllLinesParser interpreter = new OutputInterpreter.AllLinesParser();
        if (!doScript(_getStatusCommand, interpreter)) {
            s_logger.warn("Cannot get power status of " + _name + ", assume VM state was not changed");
            return null;
        }
        if (isPowerOn(interpreter.getLines())) {
            return State.Running;
        } else {
            return State.Stopped;
        }
	}
	
	protected Map<String, State> fullSync() {
		Map<String, State> changes = new HashMap<String, State>();

		if (_vmName != null) {
		    State state = getVmState();
		    if (state != null) {
		        changes.put(_vmName, state);
		    }
		}
		
		return changes;
	}
	
	@Override
	public StartupCommand[] initialize() {
		StartupRoutingCommand cmd = new StartupRoutingCommand(0, 0, 0, 0, null, Hypervisor.HypervisorType.BareMetal,
				new HashMap<String, String>(), null);
		cmd.setDataCenter(_zone);
		cmd.setPod(_pod);
		cmd.setCluster(_cluster);
		cmd.setGuid(_uuid);
		cmd.setName(_ip);
		cmd.setPrivateIpAddress(_ip);
		cmd.setStorageIpAddress(_ip);
		cmd.setVersion(BareMetalResourceBase.class.getPackage().getImplementationVersion());
		cmd.setCpus((int)_cpuNum);
		cmd.setSpeed(_cpuCapacity);
		cmd.setMemory(_memCapacity);
		cmd.setPrivateMacAddress(_mac);
		cmd.setPublicMacAddress(_mac);
		cmd.setStateChanges(fullSync());
		return new StartupCommand[] {cmd};
	}

	private boolean ipmiPing() {
		return doScript(_pingCommand);
	}
	
	@Override
	public PingCommand getCurrentStatus(long id) {
		try {
			if (!ipmiPing()) {
				Thread.sleep(1000);
				if (!ipmiPing()) {
					s_logger.warn("Cannot ping ipmi nic " + _ip);
					return null;
				}
			}
		} catch (Exception e) {
			s_logger.debug("Cannot ping ipmi nic " + _ip, e);
			return null;
		}
		 
		return new PingRoutingCommand(getType(), id, deltaSync());
	}

	protected Answer execute(IpmISetBootDevCommand cmd) {
		Script bootCmd = null;
		if (cmd.getBootDev() == BootDev.disk) {
			bootCmd = _setDiskBootCommand;
		} else if (cmd.getBootDev() == BootDev.pxe) {
			bootCmd = _setPxeBootCommand;
		} else {
			throw new CloudRuntimeException("Unkonwn boot dev " + cmd.getBootDev());
		}
		
		String bootDev = cmd.getBootDev().name();
		if (!doScript(bootCmd)) {
			s_logger.warn("Set " + _ip + " boot dev to " + bootDev + "failed");
			return new Answer(cmd, false, "Set " + _ip + " boot dev to " + bootDev + "failed");
		}
		
		s_logger.warn("Set " + _ip + " boot dev to " + bootDev + "Success");
		return new Answer(cmd, true, "Set " + _ip + " boot dev to " + bootDev + "Success");
	}
	
	protected MaintainAnswer execute(MaintainCommand cmd) {
		return new MaintainAnswer(cmd, false);
	}
	
	protected PrepareForMigrationAnswer execute(PrepareForMigrationCommand cmd) {
		return new PrepareForMigrationAnswer(cmd);
	}
	
	protected MigrateAnswer execute(MigrateCommand cmd) {   
		if (!doScript(_powerOffCommand)) {
			return new MigrateAnswer(cmd, false, "IPMI power off failed", null);
		}
		return new MigrateAnswer(cmd, true, "success", null);
	}
	
	protected CheckVirtualMachineAnswer execute(final CheckVirtualMachineCommand cmd) {
		return new CheckVirtualMachineAnswer(cmd, State.Stopped, null);
	}
	
	protected Answer execute(IpmiBootorResetCommand cmd) {
	    if (!doScript(_bootOrRebootCommand)) {
	        return new Answer(cmd ,false, "IPMI boot or reboot failed");
	    }
	    return new Answer(cmd, true, "Success");
	    
	}
	
	@Override
	public Answer executeRequest(Command cmd) {
		if (cmd instanceof ReadyCommand) {
			return execute((ReadyCommand)cmd);
		} else if (cmd instanceof StartCommand) {
			return execute((StartCommand)cmd);
		} else if (cmd instanceof StopCommand) {
			return execute((StopCommand)cmd);
		} else if (cmd instanceof RebootCommand) {
			return execute((RebootCommand)cmd);
		} else if (cmd instanceof IpmISetBootDevCommand) {
			return execute((IpmISetBootDevCommand)cmd);
		} else if (cmd instanceof MaintainCommand) {
			return execute((MaintainCommand)cmd);
		} else if (cmd instanceof PrepareForMigrationCommand) {
			return execute((PrepareForMigrationCommand)cmd);
		} else if (cmd instanceof MigrateCommand) {
			return execute((MigrateCommand)cmd);
		} else if (cmd instanceof CheckVirtualMachineCommand) {
			return execute((CheckVirtualMachineCommand)cmd);
		} else if (cmd instanceof IpmiBootorResetCommand) {
		    return execute((IpmiBootorResetCommand)cmd);
		} else {
			return Answer.createUnsupportedCommandAnswer(cmd);
		}
	}

	protected boolean isPowerOn(String str) {
		if (str.startsWith("Chassis Power is on")) {
			return true;
		} else if (str.startsWith("Chassis Power is off")) {
			return false;
		} else {
			throw new CloudRuntimeException("Cannot parse IPMI power status " + str);
		}
	}
	
	protected RebootAnswer execute(final RebootCommand cmd) {
		if (!doScript(_rebootCommand)) {
			return new RebootAnswer(cmd, "IPMI reboot failed");
		}
		
		return new RebootAnswer(cmd, "reboot succeeded", null, null);
	}
	
	protected StopAnswer execute(final StopCommand cmd) {
        boolean success = false;
        int count = 0;
        Script powerOff = _powerOffCommand;
        
        while (count < 10) {
            if (!doScript(powerOff)) {
                break;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
            
            OutputInterpreter.AllLinesParser interpreter = new OutputInterpreter.AllLinesParser();
            if (!doScript(_getStatusCommand, interpreter)) {
                s_logger.warn("Cannot get power status of " + _name + ", assume VM state was not changed");
                break;
            }
            
            if (!isPowerOn(interpreter.getLines())) {
                success = true;
                break;
            } else {
                powerOff = _forcePowerOffCommand;
            }
                        
            count++;
        }
        		
		return success ? new StopAnswer(cmd, "Success", null, Long.valueOf(0), Long.valueOf(0)) : new StopAnswer(cmd, "IPMI power off failed");
	}

	protected StartAnswer execute(StartCommand cmd) {
		VirtualMachineTO vm = cmd.getVirtualMachine();
		State state = State.Stopped;
		
		try {
            changeVmState(vm.getName(), State.Starting);

            boolean pxeBoot = false;
            String[] bootArgs = vm.getBootArgs().split(" ");
            for (int i = 0; i < bootArgs.length; i++) {
                if (bootArgs[i].equalsIgnoreCase("PxeBoot")) {
                    pxeBoot = true;
                    break;
                }
            }

            if (pxeBoot) {
                if (!doScript(_setPxeBootCommand)) {
                    return new StartAnswer(cmd, "Set boot device to PXE failed");
                }
                s_logger.debug("Set " + vm.getHostName() + " to PXE boot successfully");
            } else {
                execute(new IpmISetBootDevCommand(BootDev.disk));
            }

            OutputInterpreter.AllLinesParser interpreter = new OutputInterpreter.AllLinesParser();
            if (!doScript(_getStatusCommand, interpreter)) {
                return new StartAnswer(cmd, "Cannot get current power status of " + _name);
            }

            if (isPowerOn(interpreter.getLines())) {
                if (pxeBoot) {
                    if (!doScript(_rebootCommand)) {
                        return new StartAnswer(cmd, "IPMI reboot failed");
                    }
                    s_logger.debug("IPMI reboot " + vm.getHostName() + " successfully");
                } else {
                    s_logger.warn("Machine " + _name + " is alreay power on, why we still get a Start command? ignore it");

                }
            } else {
                if (!doScript(_powerOnCommand)) {
                    return new StartAnswer(cmd, "IPMI power on failed");
                }
            }

            s_logger.debug("Start bare metal vm " + vm.getName() + "successfully");
            state = State.Running;
            _vmName = vm.getName();
            return new StartAnswer(cmd);
		} finally {
		    if (state != State.Stopped) {
		        changeVmState(vm.getName(), state);
		    } else {
		        removeVmState(vm.getName());
		    }
		}
	}
	
	protected HashMap<String, State> deltaSync() {
        final HashMap<String, State> changes = new HashMap<String, State>();
        
        if (_vmName == null) {
            return null;
        }
        
        State newState = getVmState();
        if (newState == null) {
            s_logger.warn("Cannot get power state of VM " + _vmName);
            return null;
        }
        
        final State oldState = removeVmState(_vmName);
        if (oldState == null) {
            changeVmState(_vmName, newState);
            changes.put(_vmName, newState);
        } else if (oldState == State.Starting) {
            if (newState == State.Running) {
                changeVmState(_vmName, newState);
            } else if (newState == State.Stopped) {
                s_logger.debug("Ignoring vm " + _vmName + " because of a lag in starting the vm.");
            }
        } else if (oldState == State.Migrating) {
            s_logger.warn("How can baremetal VM get into migrating state???");
        } else if (oldState == State.Stopping) {
            if (newState == State.Stopped) {
                changeVmState(_vmName, newState);
            } else if (newState == State.Running) {
                s_logger.debug("Ignoring vm " + _vmName + " because of a lag in stopping the vm. ");
            }
        } else if (oldState != newState) {
            changeVmState(_vmName, newState);
            changes.put(_vmName, newState);
        }
        
        return changes;
       
	}
	
	protected ReadyAnswer execute(ReadyCommand cmd) {
		// derived resource should check if the PXE server is ready 
		s_logger.debug("Bare metal resource " + _name + " is ready");
		return new ReadyAnswer(cmd);
	}
	
	@Override
	public void disconnected() {

	}

	@Override
	public IAgentControl getAgentControl() {
		return _agentControl;
	}

	@Override
	public void setAgentControl(IAgentControl agentControl) {
		_agentControl = agentControl;
	}

}
