package gitlet;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import static java.lang.System.exit;

public class CmdUtils {
    /**
     *  If gitlet is not initialized (no ./.gitlet)
     *  Print the message "Not in an initialized Gitlet directory." and exit.
     */
    static void checkRep() {
        if (!Gitlet.isInited()) {
            Utils.message("Not in an initialized Gitlet directory.");
            exit(0);
        }
    }

    static void checkArgsNum(int argsNum, String... args) {
        checkArgsRange(argsNum, argsNum + 1, args);
    }

    /**
     * Check if the length of args is in [low, high)
     * @param low lower bound of args.length (include)
     * @param high upper bound of args.length (not include)
     * @param args argument list
     */
    static void checkArgsRange(int low, int high, String... args) {
        if (args.length < low || args.length >= high) {
            Utils.message("Incorrect operands.");
            System.exit(0);
        }
    }

    static void printCommit(String commitSHA1) {
        Commit currentCommit = Commit.readObject(commitSHA1);
        String currentSHA1 = commitSHA1;

        // Setup time format
        DateFormat dateFormat = new SimpleDateFormat("EEE MMM d hh:mm:ss YYYY Z");
        String strDate = dateFormat.format(currentCommit.getTime());
        // Print log
        System.out.println("===");
        System.out.println("commit " + currentSHA1);
        if (currentCommit.hasTwoParents()) {
            String p1 = currentCommit.getParentSHA1().substring(0, 7);
            String p2 = currentCommit.getParent2SHA1().substring(0, 7);
            System.out.println("Merge: " + p1 + " " + p2);
        }
        System.out.println("Date: " + strDate);
        System.out.println(currentCommit.getMessage());
        System.out.println(); // newline
    }

    static void commitHelper(String message, String parent1SHA1, String parent2SHA1) {
        // Get the blobMap of previous commit
        Commit headCommit = Gitlet.getHeadCommit();
        HashMap<String, String> headBlobsMap = headCommit.getBlobsMap();
        // Get the current index
        HashMap<String, String> indexBlobsMap = Gitlet.getIndexMap();

        // Check if there is a change from previous commit.
        if (headBlobsMap.equals(indexBlobsMap)) {
            Utils.message(" No changes added to the commit.");
            System.exit(0);
        }
        // Setup Commit object
        // HACK: ignore the detached HEAD
        String[] parentsList;
        if (parent2SHA1 == null) {
            parentsList = new String[1];
            parentsList[0] = parent1SHA1;
        } else {
            parentsList = new String[2];
            parentsList[1] = parent2SHA1;
        }
        Commit currentCommit = new Commit(message, new Date(), parentsList, indexBlobsMap);
        // Write the current Commit
        String currentCommitSHA1 = currentCommit.writeObject();

        // Setup ref: edit HEAD -> a branch -> SHA1
        Gitlet.writeHeadSHA1(currentCommitSHA1);
    }

    /**
     * Write the new file after merge conflict to .gitlet
     * @param filename name of the file where conflict happen
     * @param content new content of that file like "<<HEAD\n===\n>>>"
     * @return SHA1 hash of the new file
     */
    static String writeConflictedMergeFile(String filename, String content) {
        // Write newContent to working directory
        File currentFile = new File("./" + filename);
        Utils.writeContents(currentFile, content);
        // Write newContent to .gitlet
        return Blob.writeFile(filename);
    }
}
