package dev.kejona.crossplatforms.permission;

import java.util.Collection;

class EmptyPermissions implements Permissions {

    static final Permissions INSTANCE = new EmptyPermissions();

    private EmptyPermissions() {

    }

    @Override
    public void registerPermissions(Collection<Permission> permissions) {

    }
}
