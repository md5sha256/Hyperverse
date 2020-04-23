//
// Hyperverse - A Minecraft world management plugin
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <http://www.gnu.org/licenses/>.
//

package se.hyperver.hyperverse.database;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

@DatabaseTable(tableName = "player_locations")
public final class PersistentLocation {

    @DatabaseField(generatedId = true)
    private int id;
    @DatabaseField(uniqueCombo = true)
    private String uuid;
    @DatabaseField(uniqueCombo = true)
    private String world;
    @DatabaseField(uniqueCombo = true)
    private int locationType;
    @DatabaseField
    private double x;
    @DatabaseField
    private double y;
    @DatabaseField
    private double z;
    @DatabaseField
    private float yaw;
    @DatabaseField
    private float pitch;

    public PersistentLocation() {
    }

    public PersistentLocation(@NotNull final String uuid, @NotNull final String world,
        @NotNull LocationType locationType, final double x, final double y, final double z,
        final float yaw, final float pitch) {
        this.uuid = Objects.requireNonNull(uuid);
        this.world = Objects.requireNonNull(world);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.locationType = locationType.ordinal();
    }

    public static PersistentLocation fromLocation(@NotNull final UUID owner,
        @NotNull final Location location) {
        return fromLocation(owner, location, LocationType.PLAYER);
    }

    public static PersistentLocation fromLocation(@NotNull final UUID owner,
        @NotNull final Location location, @NotNull final LocationType locationType) {
        return new PersistentLocation(owner.toString(),
            Objects.requireNonNull(location.getWorld()).getName(), locationType, location.getX(),
            location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public void setLocationType(LocationType locationType) {
        this.locationType = locationType.ordinal();
    }

    public LocationType getLocationType() {
        return LocationType.values()[locationType];
    }

    @NotNull public String getUuid() {
        return this.uuid;
    }

    @NotNull public String getWorld() {
        return this.world;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public float getPitch() {
        return pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    @NotNull public Location toLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    public enum LocationType {

        /**
         * Represents a normal location of a player, such as the current location.
         */
        PLAYER,
        /**
         * Represents the respawn location of a player.
         */
        RESPAWN

    }

}
