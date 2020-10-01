package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static java.lang.System.exit;

public class Gitlet {
    public static final int SHA1_LENGTH = 40;
    public static final String GITLET_PATH = "./.gitlet";
    public static final String OBJECT_PATH = "./.gitlet/objects/";
    public static final String BRANCHES_PATH = "./.gitlet/refs/";
    public static final File HEAD_FILE = new File("./.gitlet/HEAD");
    public static final File INDEX_FILE = new File("./.gitlet/index");

    static boolean isInited() {
        File f = new File(GITLET_PATH);
        return (f.exists() && f.isDirectory());
    }

    static void setupDirectory() {
        // If there is already a .gitlet, display the err message and abort
        if (isInited()) {
            Utils.message("A Gitlet version-control system already exists in the current directory.");
            exit(0);
        }

        // Create .gitlet, Dir: logs, objects, refs
        (new File(GITLET_PATH)).mkdir();
        (new File("./.gitlet/logs")).mkdir();
        (new File("./.gitlet/objects")).mkdir();
        (new File("./.gitlet/refs")).mkdir();

        // File: HEAD, INDEX, refs/master
        try {
            HEAD_FILE.createNewFile();
            INDEX_FILE.createNewFile();
            (new File(BRANCHES_PATH + "master")).createNewFile();
        } catch(IOException e) {
            Utils.message(e.getMessage());
            exit(0);
        }
    }

    /*
        Head related functions
     */

    static void writeHEAD(String content) {
        Utils.writeContents(HEAD_FILE, content);
    }

    /**
     * Write the SHA1 of new commit to the file HEAD is referring to.
     * @param commitSHA1 the SHA1 for the current commit
     */
    static void writeHeadSHA1(String commitSHA1) {
        String HEADContent = Utils.readContentsAsString(HEAD_FILE);
        // HEADContent is either 40-bit SHA1 or "/branch-name"
        if (HEADContent.charAt(0) == '/') {
            String branchName = HEADContent.substring(1);
            File branchFile = new File(BRANCHES_PATH + branchName);
            Utils.writeContents(branchFile, commitSHA1);
        } else {
            // TODO: How to commit to a detached head???
        }

    }

    /**
     * Get the reference name of the head.
     * @return The branch name the head is pointing to, if it is detached, return null/
     */
    static String getHeadRef() {
        String headContent = Utils.readContentsAsString(HEAD_FILE);
        // HEADContent is either 40-bit SHA1 or "/branch-name"
        if (headContent.charAt(0) == '/') {
            String branchName = headContent.substring(1);
            return branchName;
        } else {
            return null;
        }
    }

    /**
     * Get the SHA1 HEAD is pointing to
     * @return SHA1 of commit object the head is pointing to.
     */
    static String getHeadSHA1() {
        String HEADContent = Utils.readContentsAsString(HEAD_FILE);
        // HEADContent is either 40-bit SHA1 or "/branch-name"
        if (HEADContent.charAt(0) == '/') {
            String branchName = HEADContent.substring(1);
            File branchFile = new File(BRANCHES_PATH + branchName);
            return Utils.readContentsAsString(branchFile);
        } else {
            return HEADContent;
        }
    }

    static Commit getHeadCommit() {
        String HeadSHA1 = getHeadSHA1();
        return Commit.readObject(HeadSHA1);
    }

    /* Index related functions */

    static void setIndexMap(HashMap<String, String> blobsMap) {
        Utils.writeObject(INDEX_FILE, blobsMap);
    }

    static HashMap<String, String> getIndexMap() {
        return Utils.readObject(INDEX_FILE, (new HashMap<String, String>()).getClass());
    }

    /* Branch realated functions */

    static void writeBranch(String branchName, String sha1) {
        File branchFront = new File(BRANCHES_PATH + branchName);
        Utils.writeContents(branchFront, sha1);
    }

    static String readBranchHash(String branchName) {
        File branchFile = new File(BRANCHES_PATH + branchName);
        return Utils.readContentsAsString(branchFile);
    }

    static List<String> getBranchList() {
        return Utils.plainFilenamesIn(BRANCHES_PATH);
    }

    static List<String> getAllCommitsSHA1() {
        List<String> allCommitsSHA1List = new LinkedList<>();
        List<String> objsSHA1 = Utils.plainFilenamesIn(OBJECT_PATH);
        for (String sha1 : objsSHA1) {
            try {
                if (Commit.readObject(sha1) != null) {
                    allCommitsSHA1List.add(sha1);
                }
            } catch (IllegalArgumentException e) {
                // Just ignore.
            }
        }
        return allCommitsSHA1List;
    }

    /**
     * Return a list of SHA1 of given sha1's commit (including itself)
     * @param currentSHA1 front commit's hash
     * @return an ordered list of parents (head -> head's parent -> ...)
     *          multiple parent case: topological order
     */
    static List<String> getAllAncestors(String currentSHA1) {
        List<String> ancestorsList = new LinkedList<>();
        Queue<String> q = new LinkedList<>();
        q.add(currentSHA1);
        ancestorsList.add(currentSHA1);
        while (!q.isEmpty()) {
            String topSHA1 = q.poll();
            Commit topCommit = Commit.readObject(topSHA1);
            // topCommit.parents: null || length == 1 || length == 2
            String parent1SHA1 = topCommit.getParentSHA1();
            String parent2SHA1 = topCommit.getParent2SHA1();
            if (parent1SHA1 != null) {
                q.add(parent1SHA1);
                ancestorsList.add(parent1SHA1);
            }
            if (parent2SHA1 != null) {
                q.add(parent2SHA1);
                ancestorsList.add(parent2SHA1);
            }
        }
        return ancestorsList;
    }

    /*
        Working tree related functions.
     */

    public static HashMap<String, String> getFileMap() {
        List<String> filesList = Utils.plainFilenamesIn("./");
        HashMap<String, String> fileMap = new HashMap<String, String>();
        for (String filename : filesList) {
            fileMap.put(filename, Blob.getFileHash(filename));
        }
        return fileMap;
    }

    public static boolean hasUntrackedFile(HashMap<String, String> commitMap) {
        HashMap<String, String> workingDirMap = Gitlet.getFileMap();
        boolean[] result = {false};
        workingDirMap.forEach((fileName, fileHash) -> {
            boolean trakedInCommit = commitMap.containsKey(fileName);
            if (!trakedInCommit) {
                result[0] = true;
            }
        });
        return result[0];
    }

    /* Logging functions */

    public static void printAndExit(String msg) {
        Utils.message(msg);
        System.exit(0);
    }
}
