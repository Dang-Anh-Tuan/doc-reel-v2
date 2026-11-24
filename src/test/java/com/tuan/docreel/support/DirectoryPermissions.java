package com.tuan.docreel.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Tạm bỏ quyền ghi của một thư mục (Windows: ACE DENY; POSIX: chmod r-x), trả handle để trả lại quyền. */
public final class DirectoryPermissions {

    private DirectoryPermissions() {}

    public interface Restore extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    public static Restore denyWrite(Path dir) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> original = posix.readAttributes().permissions();
            posix.setPermissions(PosixFilePermissions.fromString("r-xr-xr-x"));
            return () -> posix.setPermissions(original);
        }
        AclFileAttributeView acl = Files.getFileAttributeView(dir, AclFileAttributeView.class);
        if (acl == null) {
            throw new IOException("Hệ thống file không hỗ trợ đổi quyền: " + dir);
        }
        List<AclEntry> original = acl.getAcl();
        UserPrincipal me = dir.getFileSystem()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        AclEntry deny = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(me)
                .setPermissions(
                        AclEntryPermission.ADD_FILE,
                        AclEntryPermission.ADD_SUBDIRECTORY,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA)
                .build();
        List<AclEntry> denied = new ArrayList<>();
        denied.add(deny);
        denied.addAll(original);
        acl.setAcl(denied);
        return () -> acl.setAcl(original);
    }
}
