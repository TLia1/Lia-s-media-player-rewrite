package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.platform.TextureService;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
//? if <1.21.5 {
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
//?} else {
/*import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
*///?}
//? if >=1.21.11 && <26.3
/*import com.mojang.blaze3d.textures.FilterMode;*/
//? if >=26.3
/*import com.mojang.renderpearl.api.textures.FilterMode;*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Textures the mod fills itself, registered with the game's texture manager under
 * {@code liasmediaplayer:dynamic/<id>} so the GUI can draw them.
 *
 * <p>Each texture has a native staging buffer of its size. An upload copies the frame into it
 * once and hands it to the GPU — the same one copy and one upload v2 made. Before 1.21.5 the
 * buffer is the mod's own and the upload is a direct OpenGL call, without reaching into a
 * {@code NativeImage}'s private pixel pointer; from 1.21.5, where textures belong to the
 * game's GPU device, it is the {@code NativeImage} of a {@code DynamicTexture}, whose pointer
 * is public by then, and the device uploads it.
 *
 * <p>Released textures are freed in {@link #flushReleases()}, which the platform calls once a
 * client tick, between frames (see {@link TextureService}).
 */
public final class McTextures implements TextureService, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(McTextures.class);

    private final Map<Long, StreamTexture> live = new HashMap<>();
    private final List<TextureRef> pending = new ArrayList<>();
    private long nextId = 1;

    @Override
    public TextureRef create(String debugName, int width, int height) {
        RenderSystem.assertOnRenderThread();
        var ref = new TextureRef(nextId++, width, height);
        var texture = new StreamTexture(ref, location(ref));
        Minecraft.getInstance().getTextureManager().register(texture.location, texture);
        live.put(ref.id(), texture);
        LOGGER.debug("Texture {} ({}) created at {}x{}", ref.id(), debugName, width, height);
        return ref;
    }

    @Override
    public boolean upload(TextureRef ref, ByteBuffer rgba) {
        RenderSystem.assertOnRenderThread();
        StreamTexture texture = live.get(ref.id());
        if (texture == null) {
            return false;
        }
        return texture.upload(rgba);
    }

    @Override
    public void release(TextureRef ref) {
        if (live.containsKey(ref.id()) && !pending.contains(ref)) {
            pending.add(ref);
        }
    }

    /** The location to draw {@code ref} with, or {@code null} if it is no longer live. */
    @Nullable ResourceLocation locationOf(TextureRef ref) {
        StreamTexture texture = live.get(ref.id());
        return texture == null ? null : texture.location;
    }

    /** Frees what was released. Between frames only: once a client tick. */
    public void flushReleases() {
        if (pending.isEmpty()) {
            return;
        }
        var manager = Minecraft.getInstance().getTextureManager();
        for (TextureRef ref : pending) {
            StreamTexture texture = live.remove(ref.id());
            if (texture == null) {
                continue;
            }
            try {
                // Closes the texture, which frees its GL name and its staging buffer.
                manager.release(texture.location);
            } catch (RuntimeException e) {
                LOGGER.debug("Could not release texture {}", texture.location, e);
            }
        }
        pending.clear();
    }

    /** Frees every texture: the game is shutting down. */
    @Override
    public void close() {
        pending.clear();
        pending.addAll(live.values().stream().map(t -> t.ref).toList());
        flushReleases();
    }

    private static ResourceLocation location(TextureRef ref) {
        return ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "dynamic/" + ref.id());
    }

    //? if <1.21.5 {
    /** An RGBA texture whose whole contents are replaced from a staging buffer, frame after frame. */
    private static final class StreamTexture extends AbstractTexture {

        private final TextureRef ref;
        private final ResourceLocation location;
        private final int width;
        private final int height;
        private @Nullable ByteBuffer staging;

        StreamTexture(TextureRef ref, ResourceLocation location) {
            this.ref = ref;
            this.location = location;
            this.width = ref.width();
            this.height = ref.height();
            this.staging = MemoryUtil.memAlloc(width * height * 4);
            TextureUtil.prepareImage(getId(), width, height);
            // Linear filtering: a picture is almost never drawn at exactly its own size.
            setFilter(true, false);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        boolean upload(ByteBuffer rgba) {
            ByteBuffer buffer = staging;
            if (buffer == null) {
                return false;
            }
            // The size is checked against the texture, not the source: a mismatch must be a
            // skipped frame, never a write past the end of native memory.
            if (rgba.capacity() != buffer.capacity()) {
                LOGGER.warn("Skipping frame: {} bytes for a {}x{} texture", rgba.capacity(), width, height);
                return false;
            }
            buffer.clear();
            buffer.put(rgba.duplicate().rewind());
            buffer.flip();
            bind();
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GlStateManager._texSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    MemoryUtil.memAddress(buffer));
            return true;
        }

        @Override
        public void load(ResourceManager resourceManager) {
            // Filled by uploads, never from resources.
        }

        @Override
        public void close() {
            ByteBuffer buffer = staging;
            staging = null;
            if (buffer != null) {
                MemoryUtil.memFree(buffer);
            }
            releaseId();
        }
    }
    //?} else {
    /*/^* An RGBA texture whose whole contents are replaced from its image, frame after frame. ^/
    private static final class StreamTexture extends DynamicTexture {

        private final TextureRef ref;
        private final ResourceLocation location;
        private final int bytes;

        StreamTexture(TextureRef ref, ResourceLocation location) {
            // Cleared, so a texture drawn before its first frame is transparent, not garbage.
            super(location::toString, ref.width(), ref.height(), true);
            this.ref = ref;
            this.location = location;
            this.bytes = ref.width() * ref.height() * 4;
            // Linear filtering, clamped: a picture is almost never drawn at exactly its own size.
            // Samplers replaced the texture's own filter between 1.21.8 and 1.21.11 (unbisected:
            // the versions between are not targets).
            //? if <1.21.11 {
            setFilter(true, false);
            setClamp(true);
            //?} else
            /^sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);^/
        }

        boolean upload(ByteBuffer rgba) {
            NativeImage image = getPixels();
            if (image == null) {
                return false;
            }
            // The size is checked against the texture, not the source: a mismatch must be a
            // skipped frame, never a write past the end of native memory.
            if (rgba.capacity() != bytes) {
                LOGGER.warn(
                        "Skipping frame: {} bytes for a {}x{} texture", rgba.capacity(), ref.width(), ref.height());
                return false;
            }
            // The image is RGBA in memory, byte for byte the frame's layout.
            MemoryUtil.memByteBuffer(image.getPointer(), bytes).put(rgba.duplicate().rewind());
            upload();
            return true;
        }
    }
    *///?}
}
