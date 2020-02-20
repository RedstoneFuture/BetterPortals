package com.lauriethefish.betterportals.runnables;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.lauriethefish.betterportals.BetterPortals;
import com.lauriethefish.betterportals.BlockConfig;
import com.lauriethefish.betterportals.Config;
import com.lauriethefish.betterportals.PlayerData;
import com.lauriethefish.betterportals.PortalDirection;
import com.lauriethefish.betterportals.PortalPos;
import com.lauriethefish.betterportals.VisibilityChecker;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

// Casts a ray from each player every tick
// If it passes through the portal, set the end of it to a redstone block
public class PlayerRayCast implements Runnable {
    private Config config;

    private BetterPortals pl;
    // Store a list of all of the currently active portals
    public List<PortalPos> portals = new ArrayList<>();

    // List to store the destinations of any removed portals, these portals will be removed next tick
    public List<Location> removedDestinations = new ArrayList<>();

    public PlayerRayCast(BetterPortals pl) {
        this.pl = pl;
        this.config = pl.config;
        // Set the task to run every tick
        pl.getServer().getScheduler().scheduleSyncRepeatingTask(pl, this, 0, 1);
    }

    // Finds the closest portal to the given player,
    // this also deletes portals if they have been broken amongst other things
    // Will return null if not portals can be found within vthe portal activation distance
    private PortalPos findClosestPortal(Player player)   {
        // Loop through all active portals and find the closest one to activate
        // This is for performance - only one portal can be active at a time
        PortalPos closestPortal = null;

        // Set the current closest distance to the portalActivationDistance so that no portals
        // further than it will be activated
        double closestDistance = config.portalActivationDistance;
        // List to store the destinations of any removed portals
        List<Location> newRemovedDestionations = new ArrayList<>();

        // Iterate through the portals
        Iterator<PortalPos> iter = portals.iterator();
        while(iter.hasNext())   {
            // Advance our iterator
            PortalPos portal = iter.next();
            // Check if the portal has any missing blocks
            // If it does, remove it and add its destination to the list
            // of portals that need to be removed.
            if(!portal.checkIfStillActive())    {
                newRemovedDestionations.add(portal.destinationPosition);
                iter.remove();
                continue;
            }
            // Check if this portal has a destination portal that has been destroyed
            // If it has, break the portal and remove it from the list
            if(removedDestinations.contains(portal.portalPosition)) {
                portal.portalPosition.getBlock().setType(Material.AIR);
                iter.remove();
                continue;
            }
            // Check that the portal is in the right world
            if(portal.portalPosition.getWorld() != player.getWorld())  {
                continue;
            }

            // Check if the portal is closer that any portal so far
            double distance = portal.portalPosition.distance(player.getLocation());
            if(distance < closestDistance) {
                closestPortal = portal;
                closestDistance = distance;
            }
        }

        // Set the removed destinations to the new ones so that they can be removed next tick
        removedDestinations = newRemovedDestionations;

        // Return the closest portal
        return closestPortal;
    }

    // Teleports the player using the given portal if the player is within the portal
    // Returns wheather the player was in the teleport area, not necessarily wheather a teleport was performed
    public boolean performPlayerTeleport(PlayerData playerData, PortalPos portal)  {
        Player player = playerData.player;

        // Get the players position as a 3D vector
        Vector playerVec = player.getLocation().toVector();

        // Check if they are inside the portal
        if(VisibilityChecker.vectorGreaterThan(playerVec, portal.portalBL)
         && VisibilityChecker.vectorGreaterThan(portal.portalTR, playerVec)) {
            if(playerData.lastUsedPortal == null) {
                // Save their velocity for later
                Vector playerVelocity = player.getVelocity().clone();
                // Move them to the other portal
                playerVec.subtract(portal.portalPosition.toVector());
                playerVec = portal.applyTransformationsDestination(playerVec);

                // Convert the vector to a location
                Location newLoc = playerVec.toLocation(portal.destinationPosition.getWorld());
                newLoc.setDirection(player.getLocation().getDirection());

                // Rotate the player if the exit portal faces a different direction
                if(portal.portalDirection != portal.destinationDirection) {
                    if(portal.portalDirection == PortalDirection.EAST_WEST)  {
                        newLoc.setYaw(newLoc.getYaw() + 90.0f);
                    }   else    {
                        newLoc.setYaw(newLoc.getYaw() - 90.0f);
                    }
                }

                // Teleport them to the modified vector
                player.teleport(newLoc);
                
                // Set their velocity back to what it was
                player.setVelocity(playerVelocity);
                playerData.lastUsedPortal = portal.destinationPosition;
                playerData.resetSurroundingBlockStates();
            }
            return true;
        }   else    {
            playerData.lastUsedPortal = null;
        }
        return false;
    }

    // This function is responsible for iterating over all of the blocks surrounding the portal,
    // and performing a raycast on each of them to check if they should be visible
    @SuppressWarnings("deprecation")
    public void iterateOverBlocks(PlayerData playerData, PortalPos portal) {
        Player player = playerData.player;
        
        // Class to check if a block is visible through the portal
        VisibilityChecker checker = new VisibilityChecker(player.getEyeLocation(), config.rayCastIncrement, config.maxRayCastDistance);
        World originWorld = portal.portalPosition.getWorld();

        // Loop through all blocks around the portal
        for(double z = config.minXZ; z < config.maxXZ; z++) {
            // If the blocks are directly adjacant to the portal, skip the rest of the loop
            // This is because these blocks tend to glitch and show as visible when they shouldn't be
            // Directly adjacant is 0 on either the x or y axis, depending on the direction of the portal
            if(portal.portalDirection == PortalDirection.EAST_WEST && z == 0.0) {continue;}
            for(double y = config.minY; y < config.maxY; y++) {
                for(double x = config.minXZ; x < config.maxXZ; x++) {
                    if(portal.portalDirection == PortalDirection.NORTH_SOUTH && x == 0.0) {continue;}
                    Vector position = new Vector(x, y, z);

                    // Get the position of the blocks at portal a
                    Vector aRelativeVec = portal.applyTransformationsOrigin(position.clone());
                    if(portal.portalDirection == PortalDirection.EAST_WEST)  {
                        aRelativeVec.setX(aRelativeVec.getX() + 0.5);
                    }   else    {
                        aRelativeVec.setZ(aRelativeVec.getZ() + 0.5);
                    }
                    
                    // Convert it to a location for use later
                    Location aRelativeLoc = aRelativeVec.toLocation(originWorld);

                    // Calculate the location of the blocks at portal b
                    Location bRelativeLoc = portal.applyTransformationsDestination(position).toLocation(portal.destinationPosition.getWorld());   
                
                    // Check if the block is visible
                    boolean visible = checker.checkIfBlockVisible(aRelativeVec, portal.portalBL, portal.portalTR);

                    BlockConfig newState = null;
                    // If it is visible, send the player the block relative to portal b
                    if(visible) {
                        Block block = bRelativeLoc.getBlock();

                        // Check if we are on one of the outer blocks
                        // If the block type is non-solid then we set it to black concrete to avoid
                        // blocks showing through from what is really there
                        if((x == config.minXZ || x == config.maxXZ - 1.0 ||
                            y == config.minY || y == config.maxY - 1.0 || z == config.minXZ || z == config.maxXZ - 1.0)
                            && !block.getType().isSolid()) {
                            newState = new BlockConfig(aRelativeLoc, Material.CONCRETE, (byte) 15);
                        }   else    {
                            newState = new BlockConfig(aRelativeLoc, block.getType(), block.getData());
                        }
                    }   else    {
                        // Otherwise, reset the block back to what it should be
                        Block block = aRelativeLoc.getBlock();
                        newState = new BlockConfig(aRelativeLoc, block.getType(), block.getData());
                    }

                    // Calculate the index of this block in the players block array
                    int index = ((int) (Math.floor(x) - config.minXZ)) + ((int) (y - config.minY))
                        * config.yMultip + ((int) (z - config.minXZ)) * config.zMultip;
                    BlockConfig listedState = playerData.surroundingPortalBlockStates[index];

                    boolean overwrite;
                    // If the listed state is null, then we overwrite the block,
                    // Otherwise, we check if the block is different, and only overwrite if it is
                    if(listedState == null) {
                        overwrite = true;
                    }   else    {
                        overwrite = !newState.equals(listedState);
                    }  

                    // If we are overwriting the block, change it in the player's block array and send them a block update
                    if(overwrite) {
                        playerData.surroundingPortalBlockStates[index] = newState;
                        player.sendBlockChange(aRelativeLoc, newState.material, newState.data);
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        // Loop through every online player
        for (Player player : pl.getServer().getOnlinePlayers()) {
            PlayerData playerData = pl.players.get(player.getUniqueId());

            // Find the closest portal to the player
            PortalPos portal = findClosestPortal(player);

            // If the portal that is currently active is different to the one that was active before,
            // We reset the surrounding blocks from the previous portal so that the player does not see blocks
            // where they shouldn't be
            if(playerData.lastActivePortal != portal)    {
                playerData.lastActivePortal = portal;
                playerData.resetSurroundingBlockStates();
            }

            // If no portals were found, skip the rest of the loop
            if(portal == null) {
                continue;
            }

            // Teleport the player if they are inside a portal
            // If the player teleported, exit the loop as they are no longer near the target portal
            if(performPlayerTeleport(playerData, portal))    {
                continue;
            }

            // Iterates over all of the blocks surrounding the portal
            // This function is responsible for the portal effect
            iterateOverBlocks(playerData, portal);
        }
    }
}