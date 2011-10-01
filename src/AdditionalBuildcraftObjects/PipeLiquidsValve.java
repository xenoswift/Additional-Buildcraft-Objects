package net.minecraft.src.AdditionalBuildcraftObjects;

import net.minecraft.src.Block;
import net.minecraft.src.BuildCraftCore;
import net.minecraft.src.TileEntity;
import net.minecraft.src.buildcraft.api.IPowerReceptor;
import net.minecraft.src.buildcraft.api.Orientations;
import net.minecraft.src.buildcraft.api.Position;
import net.minecraft.src.buildcraft.api.PowerProvider;
import net.minecraft.src.buildcraft.core.ILiquidContainer;
import net.minecraft.src.buildcraft.core.RedstonePowerProvider;
import net.minecraft.src.buildcraft.core.TileNetworkData;
import net.minecraft.src.buildcraft.transport.Pipe;
import net.minecraft.src.buildcraft.transport.PipeLogicWood;
import net.minecraft.src.buildcraft.transport.PipeTransportLiquids;

public class PipeLiquidsValve extends Pipe implements IPowerReceptor {
	PowerProvider powerProvider;

	public @TileNetworkData
	int liquidToExtract;

	private final int closedTexture = 0 * 16 + 0;
	private final int openTexture = 0 * 16 + 1;
	private int nextTexture = closedTexture;

	public PipeLiquidsValve(int itemID) {
		super(new PipeTransportLiquids(2, 80), new PipeLogicValve(), itemID);

		powerProvider = new RedstonePowerProvider();
		powerProvider.configure(50, 1, 64, 1, 64);
		powerProvider.configurePowerPerdition(64, 1);
	}

	@Override
	public void prepareTextureFor(Orientations connection) {
		// if (connection == Orientations.Unknown) {
		nextTexture = ((PipeLogicValve) logic).isPowered() ? openTexture : closedTexture;
		// }
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if (worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) {
			powerProvider.receiveEnergy(1);
		}
		else
			powerProvider.useEnergy(1, 1, true);
		
		int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);

		if (liquidToExtract > 0 && meta < 6) {
			Position pos = new Position(xCoord, yCoord, zCoord, Orientations.values()[meta]);
			pos.moveForwards(1);

			TileEntity tile = worldObj.getBlockTileEntity((int) pos.x, (int) pos.y, (int) pos.z);

			if (tile instanceof ILiquidContainer) {
				ILiquidContainer container = (ILiquidContainer) tile;

				int flowRate = ((PipeTransportLiquids) transport).flowRate;

				int extracted = container.empty(liquidToExtract > flowRate ? flowRate : liquidToExtract, false);

				extracted = ((PipeTransportLiquids) transport).fill(pos.orientation, extracted,
						container.getLiquidId(), true);

				container.empty(extracted, true);

				liquidToExtract -= extracted;
			}
		}
	}

	@Override
	public int getBlockTexture() {
		return nextTexture;
	}

	@Override
	public void setPowerProvider(PowerProvider provider) {
		//powerProvider = provider;
	}

	@Override
	public PowerProvider getPowerProvider() {
		return powerProvider;
	}

	@Override
	public void doWork() {
		if (powerProvider.useEnergy(1, 1, false) < 1)
			return;
			
		int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);

		if (meta > 5) {
			return;
		}

		Position pos = new Position(xCoord, yCoord, zCoord, Orientations.values()[meta]);
		pos.moveForwards(1);
		int blockId = worldObj.getBlockId((int) pos.x, (int) pos.y, (int) pos.z);
		TileEntity tile = worldObj.getBlockTileEntity((int) pos.x, (int) pos.y, (int) pos.z);

		if (tile == null || !(tile instanceof ILiquidContainer)
				|| PipeLogicWood.isExcludedFromExtraction(Block.blocksList[blockId])) {
			return;
		}

		if (tile instanceof ILiquidContainer) {
			if (liquidToExtract <= BuildCraftCore.BUCKET_VOLUME) {
				liquidToExtract += powerProvider.useEnergy(1, 1, true) * BuildCraftCore.BUCKET_VOLUME;

				// sendNetworkUpdate();
			}
		}

	}

	@Override
	public int powerRequest() {
		return getPowerProvider().maxEnergyReceived;
	}
}