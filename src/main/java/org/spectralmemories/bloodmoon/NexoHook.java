package org.spectralmemories.bloodmoon;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Items custom de Nexo (nexo/&lt;id&gt; en DropItemList y en las recompensas). Se usa por reflexion para
 * que BloodMoon no dependa de Nexo: sin Nexo instalado, esos items simplemente no se crean.
 */
public final class NexoHook
{
    private NexoHook() {}

    public static final String PREFIX = "nexo/";

    private static Method itemFromId;
    private static Method build;
    private static boolean failed = false;

    public static boolean IsNexoItem (String code)
    {
        return code != null && code.trim().toLowerCase().startsWith(PREFIX);
    }

    /**
     * @param code nexo/&lt;id&gt;
     * @return una copia nueva del item, o null si Nexo no esta o el id no existe
     */
    public static ItemStack Create (String code)
    {
        String id = code.trim().substring(PREFIX.length()).trim();
        if (id.isEmpty() || failed) return null;
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null)
        {
            Compat.warn("Nexo no esta instalado; no se puede crear el item '" + code + "'");
            return null;
        }
        try
        {
            if (itemFromId == null)
            {
                Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
                itemFromId = nexoItems.getMethod("itemFromId", String.class);
            }
            Object builder = itemFromId.invoke(null, id);
            if (builder == null)
            {
                Compat.warn("Nexo no tiene ningun item con el id '" + id + "'");
                return null;
            }
            if (build == null) build = builder.getClass().getMethod("build");
            ItemStack item = (ItemStack) build.invoke(builder);
            return item == null ? null : item.clone();
        }
        catch (ReflectiveOperationException | ClassCastException e)
        {
            failed = true;
            Compat.warn("No se pudo usar la API de Nexo (" + e + "); los items nexo/ quedan desactivados");
            return null;
        }
    }
}
