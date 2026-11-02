package com.example.freezetag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class FreezeTag extends JavaPlugin implements Listener {
    private final Set<UUID> ice = new HashSet<>(), water = new HashSet<>(), frozen = new HashSet<>();
    private boolean running = false;
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); Objects.requireNonNull(getCommand("freezetag")).setExecutor(this::command); }
    private boolean command(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("kit")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (running) { sender.sendMessage("Kits can only be selected before a round."); return true; }
            if (args.length < 2) { player.sendMessage("Available kits: frost, aqua, swift. Use /freezetag kit <name>"); return true; }
            String kit = args[1].toLowerCase(Locale.ROOT);
            if (!Set.of("frost", "aqua", "swift").contains(kit)) { player.sendMessage("Unknown kit. Choose frost, aqua or swift."); return true; }
            player.getInventory().clear();
            switch (kit) {
                case "frost" -> { player.getInventory().addItem(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.SNOWBALL, 16)); }
                case "aqua" -> { player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD), new ItemStack(Material.COOKED_BEEF, 8)); }
                case "swift" -> { player.getInventory().addItem(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.COOKED_BEEF, 4), new ItemStack(Material.LEATHER_BOOTS)); }
            }
            player.sendMessage("Selected kit: " + kit + ". Kits provide items only; team roles are assigned by admin.");
            return true;
        }
        if (!sender.hasPermission("freezetag.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length == 0) { sender.sendMessage("/freezetag kit <frost|aqua|swift>, ice|water <player>, start, stop, clear"); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("ice") || action.equals("water")) {
            if (args.length < 2) { sender.sendMessage("Specify an online player."); return true; }
            Player p = Bukkit.getPlayerExact(args[1]);
            if (p == null) { sender.sendMessage("Player not online."); return true; }
            ice.remove(p.getUniqueId()); water.remove(p.getUniqueId()); frozen.remove(p.getUniqueId());
            (action.equals("ice") ? ice : water).add(p.getUniqueId());
            Bukkit.broadcastMessage(ChatColor.AQUA + p.getName() + " is now " + action.toUpperCase(Locale.ROOT)); return true;
        }
        switch (action) {
            case "start" -> { if (ice.isEmpty() || water.isEmpty()) { sender.sendMessage("Assign at least one ice and one water player."); return true; } frozen.clear(); running = true; Bukkit.broadcastMessage(ChatColor.AQUA + "Freeze Tag started!"); }
            case "stop" -> { running = false; frozen.clear(); Bukkit.broadcastMessage("Freeze Tag stopped."); }
            case "clear" -> { running = false; frozen.clear(); ice.clear(); water.clear(); sender.sendMessage("All roles cleared."); }
            default -> sender.sendMessage("Unknown subcommand.");
        }
        return true;
    }
    @EventHandler public void onHit(EntityDamageByEntityEvent event) {
        if (!running || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) return;
        UUID a = attacker.getUniqueId(), t = target.getUniqueId();
        if (frozen.contains(a)) { event.setCancelled(true); return; }
        if (ice.contains(a) && water.contains(t)) {
            event.setCancelled(true);
            if (frozen.add(t)) { Bukkit.broadcastMessage(ChatColor.BLUE + target.getName() + " is FROZEN!"); checkWin(); }
        } else if (water.contains(a) && water.contains(t) && frozen.contains(t) && !frozen.contains(a)) {
            event.setCancelled(true); frozen.remove(t); Bukkit.broadcastMessage(ChatColor.GREEN + target.getName() + " was rescued!");
        } else if ((ice.contains(a) && ice.contains(t)) || (water.contains(a) && water.contains(t))) event.setCancelled(true);
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (!running || !frozen.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom(), to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            Location fixed = from.clone(); fixed.setYaw(to.getYaw()); fixed.setPitch(to.getPitch()); event.setTo(fixed);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { if (running && water.contains(event.getPlayer().getUniqueId())) Bukkit.getScheduler().runTask(this, this::checkWin); }
    private void checkWin() {
        if (!running) return;
        boolean any = false;
        for (UUID id : water) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) { any = true; if (!frozen.contains(id)) return; } }
        if (any) { running = false; Bukkit.broadcastMessage(ChatColor.GOLD + "ICE TEAM WINS! All online Water players are frozen!"); frozen.clear(); }
    }
}
