#!/bin/bash

#set -x
 
usage() {
  printf "Usage: %s [vhd file in secondary storage] [template directory in secondary storage] \n" $(basename $0) 
}

cleanup()
{
  if [ ! -z $snapshotdir ]; then 
    umount $snapshotdir
    if [ $? -eq 0 ];  then
      rmdir $snapshotdir
    fi
  fi
  if [ ! -z $templatedir ]; then 
    umount $templatedir
    if [ $? -eq 0 ];  then
      rmdir $templatedir
    fi
  fi
}

if [ -z $1 ]; then
  usage
  echo "2#no vhd file path"
  exit 0
else
  snapshoturl=${1%/*}
  vhdfilename=${1##*/}
fi

if [ -z $2 ]; then
  usage
  echo "3#no template path"
  exit 0
else
  templateurl=$2
fi

snapshotdir=/var/run/cloud_mount/$(uuidgen -r)
mkdir -p $snapshotdir
if [ $? -ne 0 ]; then
  echo "4#cann't make dir $snapshotdir"
  exit 0
fi

mount $snapshoturl $snapshotdir
if [ $? -ne 0 ]; then
  rmdir $snapshotdir
  echo "5#can not mount $snapshoturl to $snapshotdir"
  exit 0
fi

templatedir=/var/run/cloud_mount/$(uuidgen -r)
mkdir -p $templatedir
if [ $? -ne 0 ]; then
  templatedir=""
  cleanup
  echo "6#cann't make dir $templatedir"
  exit 0
fi

mount $templateurl $templatedir
if [ $? -ne 0 ]; then
  rmdir $templatedir
  templatedir=""
  cleanup
  echo "7#can not mount $templateurl to $templatedir"
  exit 0
fi

VHDUTIL="/opt/xensource/bin/vhd-util"

upgradeSnapshot()
{
  local ssvhd=$1
  local parent=`$VHDUTIL query -p -n $ssvhd`
  if [ $? -ne 0 ]; then
    echo "30#failed to query $ssvhd"
    cleanup
    exit 0
  fi
  if [ "${parent##*vhd has}" = " no parent" ]; then
    dd if=$templatevhd of=$snapshotdir/$templatefilename bs=2M 
    if [ $? -ne 0 ]; then
      echo "31#failed to dd $templatevhd to $snapshotdir/$templatefilenamed"
      cleanup
      exit 0
    fi

    $VHDUTIL modify -p $snapshotdir/$templatefilename -n $ssvhd
    if [ $? -ne 0 ]; then
      echo "32#failed to set parent of $ssvhd to $snapshotdir/$templatefilenamed"
      cleanup
      exit 0
    fi

    rm -f $parent
  else
    upgradeSnapshot $parent
  fi
}

templatevhd=$(ls $templatedir/*.vhd)
if [ $? -ne 0 ]; then
  echo "8#template vhd doesn't exist for $templateurl"
  cleanup
  exit 0
fi
templatefilename=${templatevhd##*/}
snapshotvhd=$snapshotdir/$vhdfilename
upgradeSnapshot $snapshotvhd
cleanup
echo "0#success"
exit 0
