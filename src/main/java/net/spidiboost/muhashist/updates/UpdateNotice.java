package net.spidiboost.muhashist.updates;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class UpdateNotice {
    private UpdateNotice() {}
    public static String downloaded(Collection<Path> destinations) {
        var names=new LinkedHashSet<String>();
        for(var destination:destinations) {
            String filename=destination.getFileName().toString();
            boolean known=false;
            for(var mod:ModCatalog.ALL)
                if(filename.toLowerCase(Locale.ROOT).startsWith(mod.name().toLowerCase(Locale.ROOT)+"-")) {
                    names.add(mod.name());known=true;break;
                }
            if(!known)names.add(filename);
        }
        if(names.isEmpty())return "Новых обновлений для скачивания нет.";
        return "В mods скачаны обновления: "+String.join(", ",names)+". Старая версия удалится после выхода; автоперезапуск выключен.";
    }
}
