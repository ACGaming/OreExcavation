package oreexcavation.handlers;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import oreexcavation.client.ExcavationKeys;
import oreexcavation.core.ExcavationSettings;
import oreexcavation.core.OreExcavation;
import oreexcavation.network.PacketExcavation;

public class EventHandler
{
	@SubscribeEvent
	public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event)
	{
		if(event.getModID().equals(OreExcavation.MODID))
		{
			ConfigHandler.config.save();
			ConfigHandler.initConfigs();
		}
	}
	
	@SubscribeEvent
	public void onBlockBreak(BlockEvent.BreakEvent event)
	{
		if(event.getWorld().isRemote || !(event.getPlayer() instanceof EntityPlayerMP))
		{
			return;
		}
		
		EntityPlayerMP player = (EntityPlayerMP)event.getPlayer();
		
		if(player.getHeldItem(EnumHand.MAIN_HAND) == null && !ExcavationSettings.openHand)
		{
			return;
		} else if(player.getHeldItem(EnumHand.MAIN_HAND) != null && ExcavationSettings.toolBlacklist.contains(Item.REGISTRY.getNameForObject(player.getHeldItem(EnumHand.MAIN_HAND).getItem()).toString()))
		{
			return;
		} else if(ExcavationSettings.blockBlacklist.contains(Block.REGISTRY.getNameForObject(event.getState().getBlock()).toString()))
		{
			return;
		}
		
		BlockPos p = event.getPos();
		IBlockState s = event.getState();
		Block b = s.getBlock();
		int m = b.getMetaFromState(s);
		
		if(ExcavationSettings.ignoreTools || b.canHarvestBlock(event.getWorld(), p, player))
		{
			MiningAgent agent = MiningScheduler.INSTANCE.getActiveAgent(player.getUniqueID());
			
			if(agent == null)
			{
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("x", p.getX());
				tag.setInteger("y", p.getY());
				tag.setInteger("z", p.getZ());
				tag.setString("block", Block.REGISTRY.getNameForObject(b).toString());
				tag.setInteger("meta", m);
				OreExcavation.instance.network.sendTo(new PacketExcavation(tag), player);
			} else
			{
				agent.appendBlock(p);
			}
		}
	}
	
	@SubscribeEvent
	public void onTick(TickEvent.WorldTickEvent event)
	{
		if(event.phase != TickEvent.Phase.END || event.world.isRemote)
		{
			return;
		}
		
		MiningScheduler.INSTANCE.tickAgents();
	}

	public static boolean isExcavating = false;
	private static int cTick = 0;
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onClientTick(TickEvent.ClientTickEvent event)
	{
		cTick = (cTick + 1)%10;
		
		if(cTick != 0 || Minecraft.getMinecraft().thePlayer == null || !isExcavating)
		{
			return;
		}
		
		boolean canContinue = true;
		
		if(ExcavationSettings.mineMode < 0)
		{
			canContinue = false;
		} else if(ExcavationSettings.mineMode == 0)
		{
			if(!ExcavationKeys.veinKey.isPressed())
			{
				canContinue = false;
			}
		} else if(ExcavationSettings.mineMode != 2 && !Minecraft.getMinecraft().thePlayer.isSneaking())
		{
			canContinue = false;
		}
		
		if(!canContinue)
		{
			isExcavating = false;
			NBTTagCompound tags = new NBTTagCompound();
			tags.setBoolean("cancel", true);
			OreExcavation.instance.network.sendToServer(new PacketExcavation(tags));
		}
	}
	
	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event)
	{
		if(event.getWorld().isRemote || event.getWorld().getMinecraftServer().isServerRunning())
		{
			return;
		}
		
		MiningScheduler.INSTANCE.resetAll();
	}
}