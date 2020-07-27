package net.automotons.items.heads;

import net.automotons.blocks.AutomotonBlockEntity;
import net.automotons.items.HeadItem;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// Extra data is breaking progress
public class DrillHeadItem extends HeadItem<Float>{
	
	public DrillHeadItem(Settings settings){
		super(settings);
	}
	
	public void tick(AutomotonBlockEntity automoton, BlockPos facing, Float breakingTime){
		World world = automoton.getWorld();
		if(automoton.engaged && world != null){
			BlockState state = world.getBlockState(facing);
			float hardness = state.getHardness(world, facing);
			if(!state.isAir() && hardness != -1){
				if(breakingTime == null)
					breakingTime = 0f;
				if(breakingTime < 9){
					// Is -1 possible for a player to have?
					world.setBlockBreakingInfo(-1, facing, (int)Math.ceil(breakingTime));
				}else{
					if(!world.isClient())
						world.breakBlock(facing, true);
					world.setBlockBreakingInfo(-1, facing, 10);
				}
				automoton.setData(breakingTime + 1.5f / hardness);
			}else
				automoton.setData(0f);
		}
	}
	
	public CompoundTag getExtraData(Float breakingTime){
		CompoundTag tag = new CompoundTag();
		tag.putFloat("breakingTime", breakingTime != null ? breakingTime : 0);
		return tag;
	}
	
	public Float readExtraData(CompoundTag tag){
		return tag.getFloat("breakingTime");
	}
}