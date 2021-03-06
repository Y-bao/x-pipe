package com.ctrip.xpipe.redis.keeper.netty;

import com.ctrip.xpipe.api.monitor.EventMonitor;
import com.ctrip.xpipe.api.observer.Observable;
import com.ctrip.xpipe.api.observer.Observer;
import com.ctrip.xpipe.netty.ByteBufReadAction;
import com.ctrip.xpipe.netty.ChannelTrafficStatisticsHandler;
import com.ctrip.xpipe.redis.keeper.RedisClient;
import com.ctrip.xpipe.redis.keeper.RedisKeeperServer;
import com.ctrip.xpipe.redis.keeper.RedisSlave;
import com.ctrip.xpipe.redis.keeper.handler.CommandHandlerManager;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * @author wenchao.meng
 *
 * 2016年4月21日 下午3:09:44
 */
public class NettyMasterHandler extends ChannelTrafficStatisticsHandler implements Observer{
	
	private RedisKeeperServer redisKeeperServer;
	
	private CommandHandlerManager commandHandlerManager;
	
	private RedisClient redisClient;
	
	public NettyMasterHandler(RedisKeeperServer redisKeeperServer, CommandHandlerManager commandHandlerManager, long trafficReportIntervalMillis) {
		super(trafficReportIntervalMillis);
		this.redisKeeperServer =  redisKeeperServer;
		this.commandHandlerManager = commandHandlerManager;
	}
	
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		
		redisClient = redisKeeperServer.clientConnected(ctx.channel());
		redisClient.addObserver(this);
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		
		redisKeeperServer.clientDisConnected(ctx.channel());
		super.channelInactive(ctx);
	}
	
	@Override
	protected void doChannelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		
		if(logger.isDebugEnabled()){
			logger.debug(String.format("0X%X, %s", msg.hashCode(), msg.getClass()));
		}

		byteBufReadPolicy.read(ctx.channel(), (ByteBuf)msg, new ByteBufReadAction() {
			
			@Override
			public void read(Channel channel, ByteBuf byteBuf) throws Exception {
				
				String []args= redisClient.readCommands(byteBuf);
				if(args != null){
					commandHandlerManager.handle(args, redisClient);;
				}
				
			}
		});
	}


	@Override
	public void update(Object args, Observable observable) {
		
		if(args instanceof RedisSlave){
		    reportTraffic();
		    
		    RedisSlave slave = (RedisSlave)args;
		    redisClient = slave;
			logger.info("[update][become redis slave]{}", args);
		}
	}
	
	 @Override
    protected void doReportTraffic(long readBytes, long writtenBytes, String remoteIp, int remotePort) {
        if (writtenBytes > 0) {
            String type = String.format("Keeper.Out.%s", redisKeeperServer.getClusterId());
            String name = null;
            if(redisClient instanceof RedisSlave){
            	RedisSlave slave = (RedisSlave)redisClient;
            	 name = String.format("slave.%s.%s.%s:%s", slave.roleDesc(), redisKeeperServer.getShardId(), remoteIp, slave.getSlaveListeningPort());
            }else{
            	name = String.format("client.%s.%s", redisKeeperServer.getShardId(), remoteIp);
            }
            EventMonitor.DEFAULT.logEvent(type, name, writtenBytes);
        }
    }


    @Override
    protected void doWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        
    }
}
