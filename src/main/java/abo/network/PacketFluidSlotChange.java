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

package abo.network;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import buildcraft.transport.TileGenericPipe;

public class PacketFluidSlotChange extends ABOPacket {

	private byte	slot;
	private Fluid	fluid;

	public PacketFluidSlotChange(int xCoord, int yCoord, int zCoord, int slot, Fluid fluid) {
		super(ABOPacketIds.LiquidSlotChange, xCoord, yCoord, zCoord);
		this.slot = (byte) slot;
		this.fluid = fluid;
	}

	public PacketFluidSlotChange() {}

	@Override
	public void writeData(ByteBuf data) {
		super.writeData(data);

		data.writeByte(slot);

		if (fluid == null)
			data.writeShort(0);
		else {
			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setString("FluidName", fluid.getName());

			try {
				writeNBTTagCompoundToBuffer(nbt, data);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void readData(ByteBuf data) {
		super.readData(data);

		this.slot = data.readByte();

		short length = data.readShort();
		if (length == 0)
			fluid = null;
		else {
			byte[] compressed = new byte[length];
			data.readBytes(compressed);
			NBTTagCompound nbt;
			try {
				nbt = readNBTTagCompoundFromBuffer(data);
				fluid = FluidRegistry.getFluid(nbt.getString("FluidName"));
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	public void writeNBTTagCompoundToBuffer(NBTTagCompound nbt, ByteBuf data) throws IOException {
		if (nbt == null) {
			data.writeShort(-1);
		} else {
			byte[] abyte = CompressedStreamTools.compress(nbt);
			data.writeShort((short) abyte.length);
			data.writeBytes(abyte);
		}
	}

	/**
	 * Reads a compressed NBTTagCompound from this buffer
	 */
	public NBTTagCompound readNBTTagCompoundFromBuffer(ByteBuf data) throws IOException {
		short short1 = data.readShort();

		if (short1 < 0) {
			return null;
		} else {
			byte[] abyte = new byte[short1];
			data.readBytes(abyte);
			return CompressedStreamTools.func_152457_a(abyte, new NBTSizeTracker(2097152L));
		}
	}

	public void update(EntityPlayer player) {
		TileGenericPipe pipe = getPipe(player.worldObj, posX, posY, posZ);
		if (pipe == null || pipe.pipe == null) return;

		if (!(pipe.pipe instanceof IFluidSlotChange)) return;

		((IFluidSlotChange) pipe.pipe).update(slot, fluid);
	}
}
