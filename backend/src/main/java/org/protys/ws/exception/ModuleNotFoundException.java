package org.protys.ws.exception;

import java.util.UUID;

/** El módulo pedido no está registrado; los controladores responden 404 con el mensaje. */
public class ModuleNotFoundException extends ProtysMappingException {
    public ModuleNotFoundException(UUID moduleId) {
        super("Module not found: " + moduleId);
    }
}
