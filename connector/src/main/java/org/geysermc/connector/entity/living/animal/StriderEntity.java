/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 *  @author GeyserMC
 *  @link https://github.com/GeyserMC/Geyser
 *
 */

package org.geysermc.connector.entity.living.animal;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientVehicleMovePacket;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import lombok.Getter;
import org.geysermc.connector.entity.Entity;
import org.geysermc.connector.entity.type.EntityType;
import org.geysermc.connector.entity.type.TemptedEntity;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.item.ItemRegistry;

import java.util.concurrent.TimeUnit;

public class StriderEntity extends AnimalEntity implements TemptedEntity {

    private boolean shaking = false;
    @Getter
    private boolean tempted = false;

    public StriderEntity(long entityId, long geyserId, EntityType entityType, Vector3f position, Vector3f motion, Vector3f rotation) {
        super(entityId, geyserId, entityType, position, motion, rotation);

        metadata.getFlags().setFlag(EntityFlag.FIRE_IMMUNE, true);
        metadata.getFlags().setFlag(EntityFlag.BREATHING, true);
    }

    @Override
    public void updateBedrockMetadata(EntityMetadata entityMetadata, GeyserSession session) {
        if (entityMetadata.getId() == 17) {
            shaking = (boolean) entityMetadata.getValue();
        }
        if (entityMetadata.getId() == 18) {
            metadata.getFlags().setFlag(EntityFlag.SADDLED, (boolean) entityMetadata.getValue());
        }

        super.updateBedrockMetadata(entityMetadata, session);
    }

    @Override
    public void updateBedrockMetadata(GeyserSession session) {
        // Make sure they are not shaking when riding another entity
        // Needs to copy the parent state
        if (metadata.getFlags().getFlag(EntityFlag.RIDING)) {
            boolean parentShaking = false;
            for (Entity ent : session.getEntityCache().getEntities().values()) {
                if (ent.getPassengers().contains(entityId) && ent instanceof StriderEntity) {
                    parentShaking = ent.getMetadata().getFlags().getFlag(EntityFlag.SHAKING);
                    break;
                }
            }
    
            metadata.getFlags().setFlag(EntityFlag.BREATHING, !parentShaking);
            metadata.getFlags().setFlag(EntityFlag.SHAKING, parentShaking);
        } else {
            metadata.getFlags().setFlag(EntityFlag.BREATHING, !shaking);
            metadata.getFlags().setFlag(EntityFlag.SHAKING, shaking);
        }

        // Update the passengers if we have any
        for (long passenger : passengers) {
            session.getEntityCache().getEntityByJavaId(passenger).updateBedrockMetadata(session);
        }

        super.updateBedrockMetadata(session);
    }

    /**
     * Called when the rider has an equipment change
     * @param session GeyserSession
     */
    @Override
    public void riderEquipmentUpdated(GeyserSession session) {
        // Only interested if the held item is a Warped Fungus on a Stick
        ItemStack item = session.getInventory().getItemInHand();
        if (item == null || item.getId() != ItemRegistry.ITEM_ENTRIES.get(ItemRegistry.FUNGUS_ON_STICK_INDEX).getJavaId()) {
            tempted = false;
            return;
        }

        if (tempted) {
            return;
        }

        tempted = true;
        updateVehicle(session);
    }

    private void updateVehicle(GeyserSession session) {
        if (!tempted)
            return;

        Vector3f playerRotation = session.getPlayerEntity().getRotation();
        Vector3f playerVector = Vector3f.from(
                Math.cos(Math.toRadians(playerRotation.getX()+90)),
                0,
                Math.sin(Math.toRadians(playerRotation.getX()+90))
        );
        Vector3f movement = position.clone().add(playerVector.clone().mul(4.19f/20f)); // Strider Average speed: 4.19 blocks per second? (TODO)

        ClientVehicleMovePacket packet = new ClientVehicleMovePacket(
                movement.getX(),
                movement.getY(),
                movement.getZ(),
                playerRotation.getX(),
                rotation.getY()
        );
        session.sendDownstreamPacket(packet);
        session.getConnector().getGeneralThreadPool().schedule(() -> updateVehicle(session), 50, TimeUnit.MILLISECONDS); // Once per tick
    }
}
