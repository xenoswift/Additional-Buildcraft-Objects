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

package abo.pipes.fluids;

import java.util.LinkedList;
import java.util.Map;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import abo.ABO;
import abo.PipeIconProvider;
import abo.actions.ABOEnergyPulser;
import abo.actions.ActionSwitchOnPipe;
import abo.actions.ActionToggleOffPipe;
import abo.actions.ActionToggleOnPipe;

import buildcraft.api.core.IIconProvider;
import buildcraft.api.core.NetworkData;
import buildcraft.api.core.Position;
import buildcraft.api.gates.IAction;
import buildcraft.api.mj.IBatteryObject;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.power.IPowerReceptor;
import buildcraft.api.power.PowerHandler;
import buildcraft.api.power.PowerHandler.PowerReceiver;
import buildcraft.api.power.PowerHandler.Type;
import buildcraft.api.transport.IPipeTile;
import buildcraft.api.transport.PipeManager;
import buildcraft.transport.BlockGenericPipe;
import buildcraft.transport.ISolidSideTile;
import buildcraft.transport.Pipe;
import buildcraft.transport.PipeTransportFluids;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TileGenericPipe;
import buildcraft.transport.pipes.PipeFluidsWood;
import buildcraft.transport.pipes.PipeLogicWood;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PipeFluidsValve extends Pipe<PipeTransportFluids> implements ISolidSideTile{

	private boolean powered;
	private boolean switched;
	private boolean toggled;
	
	@NetworkData
	public int liquidToExtract;

	private final int closedTexture = PipeIconProvider.PipeLiquidsValveClosed;
	private final int closedTextureSide = PipeIconProvider.PipeLiquidsValveClosedSide;
	private final int openTexture = PipeIconProvider.PipeLiquidsValveOpen;
	private final int openTextureSide = PipeIconProvider.PipeLiquidsValveOpenSide;
	
	private long lastMining = 0;
	private boolean lastPower = false;
	@MjBattery(maxCapacity = 250, maxReceivedPerCycle = 100, minimumConsumption = 0)
	private double mjStored = 0;

	private PipeLogicWood logic = new PipeLogicWood(this) {
		@Override
		protected boolean isValidConnectingTile(TileEntity tile) {
			if (tile instanceof IPipeTile) {
				return false;
			}
			if (!(tile instanceof IFluidHandler)) {
				return false;
			}
			if (!PipeManager.canExtractFluids(pipe, tile.getWorldObj (), tile.xCoord, tile.yCoord, tile.zCoord)) {
				return false;
			}
			//if(container.getBlockMetadata() != getDirectionToTile(tile).ordinal()) return false;
			return true;
		}

	};
	
	public PipeFluidsValve(Item itemID) {
		super(new PipeTransportFluids(), itemID);

		transport.flowRate = 160;
		transport.travelDelay = 2;

	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIconProvider getIconProvider() {
		return ABO.instance.pipeIconProvider;
	}

	@Override
	public int getIconIndex(ForgeDirection direction) {
		if (direction == ForgeDirection.UNKNOWN)
			return isPowered() ? openTexture : closedTexture;
		else {
			int metadata = container.getBlockMetadata();

			if (metadata == direction.ordinal())
				return isPowered() ? openTextureSide : closedTextureSide;
			else
				return isPowered() ? openTexture : closedTexture;
		}
	}

	public boolean isPowered() {
		return powered || switched || toggled;
	}

	public void updateRedstoneCurrent() {
		boolean lastPowered = powered;

		LinkedList<TileGenericPipe> neighbours = new LinkedList<TileGenericPipe>();
		neighbours.add(this.container);

		powered = false;
		for (ForgeDirection o : ForgeDirection.VALID_DIRECTIONS) {
			Position pos = new Position(container.xCoord, container.yCoord, container.zCoord, o);
			pos.moveForwards(1.0);

			TileEntity tile = container.getTile(o);

			if (tile instanceof TileGenericPipe) {
				TileGenericPipe pipe = (TileGenericPipe) tile;
				if (BlockGenericPipe.isValid(pipe.pipe)) {
					neighbours.add(pipe);
					if (pipe.pipe.hasGate() && pipe.pipe.gate.getRedstoneOutput() > 0)
						powered = true;
				}
			}
		}

		if (!powered)
			powered = container.getWorldObj().isBlockIndirectlyGettingPowered(container.xCoord, container.yCoord, container.zCoord);

		if (lastPowered != powered) {
			for (TileGenericPipe pipe : neighbours) {
				pipe.scheduleNeighborChange();
				pipe.updateEntity();
			}
		}
	}
	
	

	@Override
	public void onNeighborBlockChange(int blockId) {
		logic.onNeighborBlockChange(blockId);
		super.onNeighborBlockChange(blockId);
		updateRedstoneCurrent();
	}
	
	@Override
	public boolean blockActivated(EntityPlayer entityplayer) {
		boolean flag = logic.blockActivated(entityplayer);
		if(flag) container.scheduleNeighborChange();
		return flag;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);

		nbttagcompound.setBoolean("powered", powered);
		nbttagcompound.setBoolean("switched", switched);
		nbttagcompound.setBoolean("toggled", toggled);

	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);

		powered = nbttagcompound.getBoolean("powered");
		switched = nbttagcompound.getBoolean("switched");
		toggled = nbttagcompound.getBoolean("toggled");

	}
	
	@Override
	public void initialize() {
		logic.initialize();
		super.initialize();
	}

	@Override
	public void updateEntity() {
		updateRedstoneCurrent();

		if (isPowered())
			mjStored += 1.0D;
		else {
			liquidToExtract = 0;
		}

		super.updateEntity();

		int meta = container.getBlockMetadata();

		if (liquidToExtract > 0 && meta < 6) {
			ForgeDirection side = ForgeDirection.getOrientation(meta);
			TileEntity tile = container.getTile(side);

			if (tile instanceof IFluidHandler) {
				IFluidHandler fluidHandler = (IFluidHandler) tile;

				int flowRate = transport.flowRate;

				FluidStack extracted = fluidHandler.drain(side.getOpposite(), liquidToExtract > flowRate ? flowRate : liquidToExtract, false);

				int inserted = 0;
				if (extracted != null) {
					inserted = transport.fill(side, extracted, true);

					fluidHandler.drain(side.getOpposite(), inserted, true);
				}
				liquidToExtract -= inserted;
			}
		}

		if (mjStored >= 1) {
			World w = container.getWorld();

			if (meta > 5) {
				return;
			}

			TileEntity tile = container.getTile(ForgeDirection
					.getOrientation(meta));

			if (tile instanceof IFluidHandler) {
				if (!PipeManager.canExtractFluids(this, tile.getWorldObj(),
						tile.xCoord, tile.yCoord, tile.zCoord)) {
					return;
				}

				if (liquidToExtract <= FluidContainerRegistry.BUCKET_VOLUME) {
					liquidToExtract += FluidContainerRegistry.BUCKET_VOLUME;
				}
			}
			mjStored -= 1;
		}
	}

	@Override
	public LinkedList<IAction> getActions() {
		LinkedList<IAction> actions = super.getActions();
		actions.add(ABO.actionSwitchOnPipe);
		actions.add(ABO.actionToggleOnPipe);
		actions.add(ABO.actionToggleOffPipe);
		return actions;
	}

	@Override
	protected void actionsActivated(Map<IAction, Boolean> actions) {
		boolean lastSwitched = switched;
		boolean lastToggled = toggled;

		super.actionsActivated(actions);

		switched = false;
		// Activate the actions
		for (IAction i : actions.keySet()) {
			if (actions.get(i)) {
				if (i instanceof ActionSwitchOnPipe) {
					switched = true;
				} else if (i instanceof ActionToggleOnPipe) {
					toggled = true;
				} else if (i instanceof ActionToggleOffPipe) {
					toggled = false;
				}
			}
		}
		if ((lastSwitched != switched) || (lastToggled != toggled)) {
			if (lastSwitched != switched && !switched)
				toggled = false;

			container.scheduleRenderUpdate();
			updateNeighbors(true);
		}
	}
	
	@Override
	public boolean outputOpen(ForgeDirection to) {
		int meta = container.getBlockMetadata();
		return super.outputOpen(to) && meta == to.getOpposite().ordinal();
	}

	@Override
	public boolean canPipeConnect(TileEntity tile, ForgeDirection side) {
		return super.canPipeConnect(tile, side) && (container.getBlockMetadata() == side.getOpposite().ordinal() || container.getBlockMetadata() == side.ordinal())|| getWorld().getBlock(container.xCoord + side.offsetX, container.yCoord + side.offsetY, container.zCoord + side.offsetZ).getMaterial() == Material.circuits;
	}
	
	private ForgeDirection getDirectionToTile(TileEntity tile)
	{
		int x1 = container.xCoord;
		int x2 = tile.xCoord;
		int y1 = container.yCoord;
		int y2 = tile.yCoord;
		int z1 = container.zCoord;
		int z2 = tile.zCoord;
		
		// Offsets on multiple axes or same block
		if(x1 == x2 && y1 == y2 && z1 == z2) return ForgeDirection.UNKNOWN;
		if(x1 != x2 && y1 != y2 && z1 != z2) return ForgeDirection.UNKNOWN;
		if(x1 == x2 && y1 != y2 && z1 != z2) return ForgeDirection.UNKNOWN;
		if(x1 != x2 && y1 == y2 && z1 != z2) return ForgeDirection.UNKNOWN;
		if(x1 != x2 && y1 != y2 && z1 == z2) return ForgeDirection.UNKNOWN;
		
		if(x1 > x2) return ForgeDirection.WEST;
		if(x1 < x2) return ForgeDirection.EAST;
		if(y1 > y2) return ForgeDirection.DOWN;
		if(y1 < y2) return ForgeDirection.UP;
		if(z1 > z2) return ForgeDirection.NORTH;
		if(z1 < z2) return ForgeDirection.SOUTH;
		
		// android.util.Log.wtf(msg);
		return ForgeDirection.UNKNOWN;
	}
	
	@Override
	public boolean isSolidOnSide(ForgeDirection side) {
		if(getWorld().getBlock(container.xCoord + side.offsetX, container.yCoord + side.offsetY, container.zCoord + side.offsetZ).getMaterial().isReplaceable() || getWorld().getBlock(container.xCoord + side.offsetX, container.yCoord + side.offsetY, container.zCoord + side.offsetZ).getMaterial() == Material.circuits)
		{
			return true;
		}
		return false;
	}

}
