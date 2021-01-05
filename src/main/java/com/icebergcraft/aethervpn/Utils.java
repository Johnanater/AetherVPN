package com.icebergcraft.aethervpn;

import java.text.MessageFormat;

import org.bukkit.entity.Player;
import org.joda.time.DateTime;

import com.google.gson.Gson;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class Utils 
{	
	public void checkPlayer(Player player)
	{
		
		IpInfo ipInfo = getIpInfo(getPlayerIp(player));
		
		// Log joins
		if (Main.INSTANCE.CONFIG.get("logJoins").equals("true"))
		{
			Logging.LogInfo(MessageFormat.format("{0} has joined with the IP: {1} Org: {2}", player.getName(), ipInfo.ipAddress, ipInfo.org));
		}
		
		// Alert online staff members
		if (Main.INSTANCE.CONFIG.get("alertOnlineStaff").equals("true"))
		{
			for (Player staff : Main.INSTANCE.getServer().getOnlinePlayers())
			{
				if (Main.INSTANCE.hasPermission(staff, "aethervpn.alert"))
				{
					staff.sendMessage(MessageFormat.format("{0} has joined with the IP: {1} Org: {2}", player.getName(), ipInfo.ipAddress, ipInfo.org));
				}
			}
		}
		
		if (ipInfo.isHost && 
			Main.INSTANCE.CONFIG.get("blockVPNs").equals("true") &&
			!isWhitelisted(ipInfo.ipAddress) &&
			!canBypass(player))
		{
			// Log kicks
			if (Main.INSTANCE.CONFIG.get("logJoins").equals("true"))
			{
				Logging.LogInfo(MessageFormat.format("{0} has been kicked for using a VPN! (IP: {1} Org: {2})", player.getDisplayName(), ipInfo.ipAddress, ipInfo.org));
			}
			
			// Alert online staff members
			if (Main.INSTANCE.CONFIG.get("alertOnlineStaff").equals("true"))
			{
				for (Player staff : Main.INSTANCE.getServer().getOnlinePlayers())
				{
					if (Main.INSTANCE.hasPermission(staff, "aethervpn.alert"))
					{
						staff.sendMessage(MessageFormat.format("{0} has been kicked for using a VPN! (IP: {1} Org: {2})", player.getDisplayName(), ipInfo.ipAddress, ipInfo.org));
					}
				}
			}
			
			player.kickPlayer("You've been kicked for using a VPN!");
		}
	}
	
	// Get IpInfo
	public IpInfo getIpInfo(String ip)
	{
		IpInfo ipInfo;
		
		if (Main.INSTANCE.CACHE.isCached(ip))
		{
			ipInfo = Main.INSTANCE.CACHE.getCachedIpInfo(ip);
		}
		else
		{
			ipInfo = DownloadIpInfo(ip);
		}
		
		return ipInfo;
	}
	
	// Download IpInfo from API
	public IpInfo DownloadIpInfo(String ip)
	{
		String key = "";
		
		if (!Main.INSTANCE.CONFIG.get("apiKey").equals(""))
		{
			key = "/" + Main.INSTANCE.CONFIG.get("apiKey");
		}
		
		String url = MessageFormat.format("http://api.vpnblocker.net/v2/json/{0}{1}", ip, key);
		
		try 
		{
			String jsonDownload = Unirest.get(url).asString().getBody();
			
			VPNBlockerRootObject jsonString = new Gson().fromJson(jsonDownload, VPNBlockerRootObject.class);
			
			String status = jsonString.getStatus();
			
			//System.out.println(jsonDownload);
			
			// Success!
			if (status.equals("success"))
			{
				// only check remaining if there is an api key
				if (key.equals(""))
				{
					if (jsonString.getRemainingRequests() <= Integer.parseInt(Main.INSTANCE.CONFIG.get("remainingRequestsWarning")))
					{
						Logging.LogInfo(MessageFormat.format("You have {0} VPNBlocker.net requests left!", jsonString.getRemainingRequests()));
					}
				}
				
				IpInfo ipInfo = new IpInfo();
				
				ipInfo.ipAddress = jsonString.getIpaddress();
				ipInfo.isHost = jsonString.getHostIp();
				ipInfo.org = jsonString.getOrg();
				ipInfo.instant = DateTime.now().toInstant();
				
				if (Main.INSTANCE.CONFIG.get("useCache").equals("true"))
				{
					Main.INSTANCE.CACHE.addToCache(ipInfo);
				}
				return ipInfo;
			}
			
			// The query returned failed
			String msg = jsonString.getMsg();
			
			if (status.equals("failed") && !msg.equals("Invalid IP Address"))
			{
				Logging.LogError("VPNBlocker.net API returned failed! Status Message: " + msg);
				
				if (msg.equals("Monthly Request Limit Reached"))
				{
					Logging.LogError("You have no more VPNBlocket.net requests left! This plugin will only used cached IPs!");
				}
			}			
		}
		// API ded
		catch (UnirestException ex)
		{
			Logging.LogError("Error with the VPNBlocker.net API!");
			ex.printStackTrace();
		}
		return null;
	}
	
	// Get the actual IP of a player
	public String getPlayerIp(Player player)
	{
		String ip = (player.getAddress().getAddress().toString()).replaceAll("/", "");
		return ip;
	}

	// Check if user has permission to bypass
	public boolean canBypass(Player player)
	{
		if (Main.INSTANCE.hasPermission(player, "aethervpn.bypass"))
			return true;

		return player.isOp();
	}
	
	// Check if an IP is whitelisted in the config
	public boolean isWhitelisted(String ip)
	{
		String[] whitelistedIps = Main.INSTANCE.CONFIG.get("whitelistedIps").split(",");
		
		for (String whitelistedIp : whitelistedIps)
		{
			if (ip.equalsIgnoreCase(whitelistedIp))
				return true;
		}
		return false;
	}
}
