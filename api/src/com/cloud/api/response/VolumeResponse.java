/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
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
package com.cloud.api.response;

import java.util.Date;

import com.cloud.api.ApiConstants;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class VolumeResponse extends BaseResponse implements ControlledEntityResponse{
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the disk volume")
    private Long id;

    @SerializedName(ApiConstants.JOB_ID)
    @Param(description = "shows the current pending asynchronous job ID. This tag is not returned if no current pending jobs are acting on the volume")
    private Long jobId;

    @SerializedName(ApiConstants.JOB_STATUS)
    @Param(description = "shows the current pending asynchronous job status")
    private Integer jobStatus;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "name of the disk volume")
    private String name;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "ID of the availability zone")
    private Long zoneId;

    @SerializedName("zonename")
    @Param(description = "name of the availability zone")
    private String zoneName;

    @SerializedName(ApiConstants.TYPE)
    @Param(description = "type of the disk volume (ROOT or DATADISK)")
    private String volumeType;

    @SerializedName(ApiConstants.DEVICE_ID)
    @Param(description = "the ID of the device on user vm the volume is attahed to. This tag is not returned when the volume is detached.")
    private Long deviceId;

    @SerializedName(ApiConstants.VIRTUAL_MACHINE_ID)
    @Param(description = "id of the virtual machine")
    private Long virtualMachineId;

    @SerializedName("vmname")
    @Param(description = "name of the virtual machine")
    private String virtualMachineName;

    @SerializedName("vmdisplayname")
    @Param(description = "display name of the virtual machine")
    private String virtualMachineDisplayName;

    @SerializedName("vmstate")
    @Param(description = "state of the virtual machine")
    private String virtualMachineState;

    @SerializedName(ApiConstants.SIZE)
    @Param(description = "size of the disk volume")
    private Long size;

    @SerializedName(ApiConstants.CREATED)
    @Param(description = "the date the disk volume was created")
    private Date created;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the state of the disk volume")
    private String state;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account associated with the disk volume")
    private String accountName;
    
    @SerializedName(ApiConstants.PROJECT_ID) @Param(description="the project id of the vpn")
    private Long projectId;
    
    @SerializedName(ApiConstants.PROJECT) @Param(description="the project name of the vpn")
    private String projectName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the ID of the domain associated with the disk volume")
    private Long domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the domain associated with the disk volume")
    private String domainName;

    @SerializedName("storagetype")
    @Param(description = "shared or local storage")
    private String storageType;

    @SerializedName(ApiConstants.HYPERVISOR)
    @Param(description = "Hypervisor the volume belongs to")
    private String hypervisor;

    @SerializedName(ApiConstants.DISK_OFFERING_ID)
    @Param(description = "ID of the disk offering")
    private Long diskOfferingId;

    @SerializedName("diskofferingname")
    @Param(description = "name of the disk offering")
    private String diskOfferingName;

    @SerializedName("diskofferingdisplaytext")
    @Param(description = "the display text of the disk offering")
    private String diskOfferingDisplayText;

    @SerializedName("storage")
    @Param(description = "name of the primary storage hosting the disk volume")
    private String storagePoolName;

    @SerializedName(ApiConstants.SNAPSHOT_ID)
    @Param(description = "ID of the snapshot from which this volume was created")
    private Long snapshotId;

    @SerializedName("attached")
    @Param(description = "the date the volume was attached to a VM instance")
    private Date attached;

    @SerializedName("destroyed")
    @Param(description = "the boolean state of whether the volume is destroyed or not")
    private Boolean destroyed;

    @SerializedName(ApiConstants.SERVICE_OFFERING_ID)
    @Param(description = "ID of the service offering for root disk")
    private Long serviceOfferingId;

    @SerializedName("serviceofferingname")
    @Param(description = "name of the service offering for root disk")
    private String serviceOfferingName;

    @SerializedName("serviceofferingdisplaytext")
    @Param(description = "the display text of the service offering for root disk")
    private String serviceOfferingDisplayText;

    @SerializedName("isextractable")
    @Param(description = "true if the volume is extractable, false otherwise")
    private Boolean extractable;

    @Override
    public Long getObjectId() {
        return getId();
    }

    @Override
    public Long getJobId() {
        return jobId;
    }

    @Override
    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    @Override
    public Integer getJobStatus() {
        return jobStatus;
    }

    @Override
    public void setJobStatus(Integer jobStatus) {
        this.jobStatus = jobStatus;
    }

    public Boolean getDestroyed() {
        return destroyed;
    }

    public void setDestroyed(Boolean destroyed) {
        this.destroyed = destroyed;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }
    
    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public void setVolumeType(String volumeType) {
        this.volumeType = volumeType;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public void setVirtualMachineId(Long virtualMachineId) {
        this.virtualMachineId = virtualMachineId;
    }

    public void setVirtualMachineName(String virtualMachineName) {
        this.virtualMachineName = virtualMachineName;
    }

    public void setVirtualMachineDisplayName(String virtualMachineDisplayName) {
        this.virtualMachineDisplayName = virtualMachineDisplayName;
    }

    public void setVirtualMachineState(String virtualMachineState) {
        this.virtualMachineState = virtualMachineState;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setDomainId(Long domainId) {
        this.domainId = domainId;
    }
    
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public void setHypervisor(String hypervisor) {
        this.hypervisor = hypervisor;
    }

    public void setDiskOfferingId(Long diskOfferingId) {
        this.diskOfferingId = diskOfferingId;
    }

    public void setDiskOfferingName(String diskOfferingName) {
        this.diskOfferingName = diskOfferingName;
    }

    public void setDiskOfferingDisplayText(String diskOfferingDisplayText) {
        this.diskOfferingDisplayText = diskOfferingDisplayText;
    }

    public void setStoragePoolName(String storagePoolName) {
        this.storagePoolName = storagePoolName;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public void setAttached(Date attached) {
        this.attached = attached;
    }

    public void setServiceOfferingId(Long serviceOfferingId) {
        this.serviceOfferingId = serviceOfferingId;
    }

    public void setServiceOfferingName(String serviceOfferingName) {
        this.serviceOfferingName = serviceOfferingName;
    }

    public void setServiceOfferingDisplayText(String serviceOfferingDisplayText) {
        this.serviceOfferingDisplayText = serviceOfferingDisplayText;
    }

    public void setExtractable(Boolean extractable) {
        this.extractable = extractable;
    }

    public void setState(String state) {
        this.state = state;
    }
    
    @Override
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }   
}
