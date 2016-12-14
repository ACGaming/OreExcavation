package oreexcavation.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import oreexcavation.core.ExcavationSettings;
import oreexcavation.shapes.ExcavateShape;
import oreexcavation.utils.BlockPos;
import com.google.common.base.Stopwatch;

public class MiningScheduler
{
	public static final MiningScheduler INSTANCE = new MiningScheduler();
	
	private HashMap<UUID,MiningAgent> agents = new HashMap<UUID,MiningAgent>();
	private Stopwatch timer;
	
	private MiningScheduler()
	{
		timer = Stopwatch.createStarted();
	}
	
	public MiningAgent getActiveAgent(UUID uuid)
	{
		return agents.get(uuid);
	}
	
	public void stopMining(EntityPlayerMP player)
	{
		MiningAgent a = agents.get(player.getUniqueID());
		
		if(a != null)
		{
			a.dropEverything();
		}
		
		agents.remove(player.getUniqueID());
	}
	
	public MiningAgent startMining(EntityPlayerMP player, BlockPos pos, Block block, int meta, ExcavateShape shape)
	{
		MiningAgent existing = agents.get(player.getUniqueID());
		
		if(existing != null)
		{
			existing.appendBlock(pos);
		} else
		{
			existing = new MiningAgent(player, pos, block, meta);
			agents.put(player.getUniqueID(), existing);
			
			if(shape != null)
			{
				existing.setShape(shape, ExcavateShape.getFacing(player));
			}
			
			existing.init();
		}
		
		return existing;
	}
	
	public void tickAgents()
	{
		List<Entry<UUID,MiningAgent>> list = new ArrayList<Entry<UUID,MiningAgent>>(agents.entrySet());
		
		timer.reset();
		timer.start();
		
		for(int i = list.size() - 1; i >= 0; i--)
		{
			if(ExcavationSettings.tpsGuard && timer.elapsed(TimeUnit.MILLISECONDS) > 40)
			{
				EventHandler.skipNext = true;
				break;
			}
			
			Entry<UUID,MiningAgent> entry = list.get(i);
			
			MiningAgent a = entry.getValue();
			
			EventHandler.captureAgent = a;
			boolean complete = a.tickMiner();
			EventHandler.captureAgent = null;
			
			if(complete)
			{
				a.dropEverything();
				agents.remove(entry.getKey());
			}
		}
		
		timer.stop();
	}
	
	public void resetAll()
	{
		agents.clear();
	}
}
