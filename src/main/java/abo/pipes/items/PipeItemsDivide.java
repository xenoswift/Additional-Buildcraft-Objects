/** 
 * Copyright (C) 2011-2013 Flow86
 * 
 * AdditionalBuildcraftObjects is open-source.
 *
 * It is distributed under the terms of my Open Source License. 
 * It grants rights to read, modify, compile or run the code. 
 * It does *NOT* grant the right to redistribute this software or its 
 * modifications in any form, binary or source, except if expressively
 * granted by the copyright holder.
 */

package abo.pipes.items;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Random;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import abo.PipeIconProvider;
import abo.pipes.ABOPipe;

import buildcraft.api.core.Position;
import buildcraft.api.gates.ITrigger;
import buildcraft.api.gates.ITriggerParameter;
import buildcraft.api.transport.IPipeConnection;
import buildcraft.api.transport.IPipeTile.PipeType;
import buildcraft.core.ItemWrench;
import buildcraft.transport.IPipeTransportItemsHook;
import buildcraft.transport.IPipeTrigger;
import buildcraft.transport.Pipe;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TransportConstants;
import buildcraft.transport.TravelingItem;

/**
 * @author Flow86
 * 
 */
public class PipeItemsDivide extends ABOPipe<PipeTransportItems> implements IPipeTransportItemsHook {

	private byte desiredSize = 1;
	
	public PipeItemsDivide(Item itemID) {
		super(new PipeTransportItems(), itemID);
	}

	@Override
	public int getIconIndex(ForgeDirection direction) {
		return PipeIconProvider.PipeItemsDivide;
	}

	@Override
	public void entityEntered(TravelingItem item, ForgeDirection orientation) {
		ItemStack stack = item.getItemStack();
		while(stack.stackSize > desiredSize)
		{
			ItemStack newStack = stack.splitStack(desiredSize);
			TravelingItem newItem = copyTravelingItem(item, newStack);
			newItem.getExtraData().setBoolean("DONT MERGE ME", true);
			if (transport.canReceivePipeObjects(orientation, newItem))
			{
				newItem.input = newItem.output;
			}
			transport.injectItem(newItem, orientation);
			readjustSpeed(newItem, 5);
		}
		if(stack.stackSize < desiredSize && stack.stackSize > 0)
		{
			item.blacklist.add(item.input);
			item.toCenter = true;
			item.input = transport.resolveDestination(item);
			
			if (!container.getWorldObj().isRemote) {
				item.output = item.input.getOpposite();
			}
			transport.items.unscheduleRemoval(item);
		}else if(stack.stackSize <= 0)
		{
			transport.items.scheduleRemoval(item);
			return;
		}
		readjustSpeed(item, 5);
	}
	
	private TravelingItem copyTravelingItem(TravelingItem item, ItemStack newStack)
	{
		TravelingItem newItem = TravelingItem.make();
		newItem.xCoord = item.xCoord;
		newItem.yCoord = item.yCoord;
		newItem.zCoord = item.zCoord;
		newItem.setSpeed(item.getSpeed());
		newItem.toCenter = item.toCenter;
		newItem.input = item.input;
		newItem.output = item.output;
		newItem.color = item.color == null ? null : item.color;

		if(item.hasExtraData())
		{
			try {
				Field field = newItem.getClass().getDeclaredField("extraData");
				field.setAccessible(true);
				NBTTagCompound nbt = (NBTTagCompound) field.get(newItem);
				field.set(newItem, item.getExtraData().copy());
				field.setAccessible(false);
			} catch (Exception e){}
			
		}
		newItem.setItemStack(newStack);
		return newItem;
	}

	@Override
	public boolean blockActivated(EntityPlayer entityplayer) {
		if(entityplayer.getCurrentEquippedItem() != null && entityplayer.getCurrentEquippedItem().getItem() instanceof ItemWrench)
		{
			incrementMeta();
			if(!container.getWorldObj().isRemote) entityplayer.addChatComponentMessage(new ChatComponentText("Set the desired stack size to "+desiredSize+"."));
			return true;
		}
		return false;
	}
	
	private void incrementMeta()
	{
		desiredSize++;
		if(desiredSize > 8) desiredSize = 1;
	}

	@Override
	public void readjustSpeed(TravelingItem item) {
		item.setSpeed(Math.min(Math.max(TransportConstants.PIPE_NORMAL_SPEED, item.getSpeed()) * 2f, TransportConstants.PIPE_NORMAL_SPEED * 20F));
	}
	
	public void readjustSpeed(TravelingItem item, int errorMargin) {
		item.setSpeed(Math.min(Math.max(TransportConstants.PIPE_NORMAL_SPEED, item.getSpeed()) * 2f, TransportConstants.PIPE_NORMAL_SPEED * 20F) + (new Random().nextInt(errorMargin) / 1000) - (new Random().nextInt(errorMargin) / 1000));
	}

	@Override
	public LinkedList<ForgeDirection> filterPossibleMovements(LinkedList<ForgeDirection> possibleOrientations,
			Position pos, TravelingItem item) { 
		LinkedList<ForgeDirection> list = new LinkedList<ForgeDirection>();

		pos.moveForwards(1.0);
		if (transport.canReceivePipeObjects(pos.orientation, item))
			list.add(pos.orientation);
		else
			list = possibleOrientations;

		return list;
	}
}
