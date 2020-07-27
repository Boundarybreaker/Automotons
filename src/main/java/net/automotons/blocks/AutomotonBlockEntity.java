package net.automotons.blocks;

import net.automotons.AutomotonsRegistry;
import net.automotons.items.Head;
import net.automotons.items.Module;
import net.automotons.screens.AutomotonScreenHandler;
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Tickable;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;

@SuppressWarnings({"unchecked", "rawtypes"})
public class AutomotonBlockEntity extends LockableContainerBlockEntity implements Tickable, BlockEntityClientSerializable, ExtendedScreenHandlerFactory{
	
	// Facing a specific direction
	public Direction facing = Direction.NORTH;
	// Whether the head is forward
	public boolean engaged = false;
	// The module currently being processed
	public int module = 0;
	// The number of ticks already spent processing the module
	public int moduleTime = 0;
	// Modules (0-11), head (12), and store slot (13) inventory
	private DefaultedList<ItemStack> inventory;
	// Used for animations and errors
	public Direction lastFacing = Direction.NORTH;
	public boolean lastEngaged = false;
	// Extra data for head
	public Object data;
	
	public AutomotonBlockEntity(){
		super(AutomotonsRegistry.AUTOMOTON_BE);
		inventory = DefaultedList.ofSize(14, ItemStack.EMPTY);
	}
	
	@SuppressWarnings("ConstantConditions")
	public void tick(){
		Module toExecute = atIndex(module);
		if(moduleTime == 0){
			// finish last instruction
			if(lastEngaged != engaged){
				// engage (happens after)
				if(engaged && getHead() != null)
					getHead().engageInto(this, pos.offset(facing), data);
				lastEngaged = engaged;
			}
			if(lastFacing != null && lastFacing != facing){
				// rotate into (happens after)
				if(getHead() != null)
					getHead().endRotationInto(this, pos.offset(facing), pos.offset(lastFacing), data);
				lastFacing = facing;
			}
			// run instruction
			// get module slot
			// first six are in order, second six are reversed
			if(toExecute != null)
				toExecute.execute(this);
			getWorld().updateNeighbors(pos, getWorld().getBlockState(pos).getBlock());
		}
		moduleTime++;
		if(moduleTime >= 10 && !(getWorld().isReceivingRedstonePower(pos) && !getWorld().isEmittingRedstonePower(pos, null))){
			moduleTime = 0;
			// move to next instruction
			module++;
		}
		toExecute = atIndex(module);
		if(toExecute == null){
			moduleTime = 0;
			// move to next instruction
			// look for next module
			if(hasNoModules())
				module = 0;
			else
				while(atIndex(module) == null){
					module++;
					if(module >= 12)
						module = 0;
				}
		}
		if(module >= 12)
			module = 0;
		if(getHead() != null)
			getHead().tick(this, pos.offset(facing), data);
	}
	
	public boolean hasNoModules(){
		return inventory.subList(0, 12).stream().allMatch(ItemStack::isEmpty);
	}
	
	public ItemStack getHeadStack(){
		return getStack(12);
	}
	
	public Head getHead(){
		Item item = getHeadStack().getItem();
		return item instanceof Head ? (Head)item : null;
	}
	
	public Module atIndex(int index){
		if(index < 6){
			if(!getStack(index).isEmpty() && getStack(index).getItem() instanceof Module)
				return (Module)getStack(index).getItem();
		}else if(index < 12){
			int revIndex = 11 - (index - 6);
			if(!getStack(revIndex).isEmpty() && getStack(revIndex).getItem() instanceof Module)
				return (Module)getStack(revIndex).getItem();
		}
		return null;
	}
	
	public boolean turnCw(){
		if(getHead() == null || getHead().canRotateInto(this, pos.offset(facing.rotateYClockwise()), pos.offset(facing), data)){
			lastFacing = facing;
			facing = facing.rotateYClockwise();
			// start rotate into (happens before)
			if(getHead() != null)
				getHead().startRotationInto(this, pos.offset(facing), pos.offset(lastFacing), data);
			return true;
		}else
			return false;
	}
	
	public boolean turnCcw(){
		if(getHead() == null || getHead().canRotateInto(this, pos.offset(facing.rotateYCounterclockwise()), pos.offset(facing), data)){
			lastFacing = facing;
			facing = facing.rotateYCounterclockwise();
			// start rotate into (happens before)
			if(getHead() != null)
				getHead().startRotationInto(this, pos.offset(facing), pos.offset(lastFacing), data);
			return true;
		}else
			return false;
	}
	
	public void setEngaged(boolean engaged){
		lastEngaged = this.engaged;
		this.engaged = engaged;
	}
	
	public CompoundTag toTag(CompoundTag tag){
		CompoundTag nbt = super.toTag(tag);
		nbt.putInt("facing", facing.getId());
		nbt.putInt("lastFacing", lastFacing.getId());
		nbt.putBoolean("engaged", engaged);
		nbt.putInt("instruction", module);
		nbt.putInt("instructionTime", moduleTime);
		Inventories.toTag(tag, inventory);
		if(getHead() != null)
			nbt.put("headData", getHead().getExtraData(data));
		return nbt;
	}
	
	public void fromTag(BlockState state, CompoundTag tag){
		super.fromTag(state, tag);
		facing = Direction.byId(tag.getInt("facing"));
		lastFacing = Direction.byId(tag.getInt("lastFacing"));
		engaged = tag.getBoolean("engaged");
		module = tag.getInt("instruction");
		moduleTime = tag.getInt("instructionTime");
		
		inventory.clear();
		Inventories.fromTag(tag, inventory);
		
		data = null;
		if(getHead() != null)
			data = getHead().readExtraData(tag.getCompound("headData"));
	}
	
	public void setData(Object data){
		this.data = data;
	}
	
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory){
		return new AutomotonScreenHandler(syncId, this, playerInventory);
	}
	
	public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf){
		// Write our location to buffer
		buf.writeBlockPos(getPos());
	}
	
	protected Text getContainerName(){
		return new TranslatableText("container.automoton");
	}
	
	public int size(){
		return 14;
	}
	
	public boolean isEmpty(){
		return inventory.stream().allMatch(ItemStack::isEmpty);
	}
	
	public ItemStack getStack(int slot){
		return inventory.get(slot);
	}
	
	public ItemStack removeStack(int slot, int amount){
		ItemStack itemStack = Inventories.splitStack(inventory, slot, amount);
		if(!itemStack.isEmpty()){
			markDirty();
			sync();
			if(slot == 12)
				data = null;
		}
		return itemStack;
	}
	
	public ItemStack removeStack(int slot){
		ItemStack stack = Inventories.removeStack(inventory, slot);
		sync();
		if(slot == 12)
			data = null;
		return stack;
	}
	
	public void setStack(int slot, ItemStack stack){
		inventory.set(slot, stack);
		if(stack.getCount() > getMaxCountPerStack())
			stack.setCount(getMaxCountPerStack());
		if(slot == 12)
			data = null;
		
		markDirty();
		sync();
	}
	
	public boolean canPlayerUse(PlayerEntity player){
		if(world == null)
			return false;
		if(world.getBlockEntity(pos) != this)
			return false;
		return player.squaredDistanceTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
	}
	
	public void clear(){
		inventory.clear();
		sync();
	}
	
	public void fromClientTag(CompoundTag tag){
		fromTag(world != null ? world.getBlockState(pos) : null, tag);
	}
	
	public CompoundTag toClientTag(CompoundTag tag){
		return toTag(tag);
	}
}