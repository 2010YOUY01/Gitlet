package gitlet;

import jdk.jshell.execution.Util;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Cmd {
    public static void init(String... args) {
        // Check args
        CmdUtils.checkArgsNum(0, args);
        // Setup directory
        Gitlet.setupDirectory();

        /*
         Create a commit with no files, message "initial commit"
         With single branch: master, and is the current branch (implement HEAD)
         timestamp: 00:00:00 UTC, Thursday, 1 January 1970
         And write the commit object
        */
        HashMap<String, String> blobsMap = new HashMap<>();
        Commit initCommit = new Commit("initial commit",
                new Date(0), null, blobsMap);
        String initCommitSHA1 = initCommit.writeObject();

        // Write index file.
        Gitlet.setIndexMap(blobsMap);

        // Update HEAD, master branch
        Gitlet.writeBranch("master", initCommitSHA1);
        Gitlet.writeHEAD("/master");

    }

    /**
     * Stage the file.
     * @param args length = 1, filename
     */
    public static void add(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(1, args);
        String filename = args[0];
        // Get the blobMap of previous commit
        Commit headCommit = Gitlet.getHeadCommit();
        HashMap<String, String> headBlobsMap = headCommit.getBlobsMap();
        // Get the current index
        HashMap<String, String> indexBlobsMap = Gitlet.getIndexMap();
        // Get the current working-tree
        HashMap<String, String> fileMap = Gitlet.getFileMap();

        /*
            1. file not exist: print "File does not exist."
            2. file exist: update index and .gitlet/Objects
         */
        if (!fileMap.containsKey(filename)) {
            Utils.message("File does not exist.");
            System.exit(0);
        } else {
            String fileHash = fileMap.get(filename);
            // Not staged
            if (!indexBlobsMap.containsKey(filename)) {
                // update index
                indexBlobsMap.put(filename, fileHash);
                Gitlet.setIndexMap(indexBlobsMap);
                // update .gitlet/Objects
                Blob.writeFile(filename);
            }
            // Staged. Note Blobs.writeFile will check if already written.
            else {
                String stagedFileHash = indexBlobsMap.get(filename);
                // case1: not changed
                if (stagedFileHash == fileHash) return;
                // case2: modified
                indexBlobsMap.put(filename, fileHash);
                Gitlet.setIndexMap(indexBlobsMap);
                Blob.writeFile(filename);
            }
        }
    }

    /**
     * Create the new commit according to the current index.
     * If nothing changed, then nothing happen.
     * @param args length == 1, message of the commit.
     */
    public static void commit(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        if (args.length == 0) {
            Utils.message("Please enter a commit message.");
            System.exit(0);
        }
        CmdUtils.checkArgsNum(1, args);
        String message = args[0];

        String prevCommitSHA1 = Gitlet.getHeadSHA1();
        CmdUtils.commitHelper(message, prevCommitSHA1, null);
    }

    /**
     * Unstage the file, will be exclude from next commit
     * If file is in current commit, then delete in working directory
     * @param args length == 1, filename
     */
    public static void rm(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(1, args);
        String filename = args[0];
        // Get the blobMap of previous commit
        Commit headCommit = Gitlet.getHeadCommit();
        HashMap<String, String> headBlobsMap = headCommit.getBlobsMap();
        // Get the current index
        HashMap<String, String> indexBlobsMap = Gitlet.getIndexMap();
        // Get the current working-tree
        HashMap<String, String> fileMap = Gitlet.getFileMap();

        boolean fileCached = indexBlobsMap.containsKey(filename);
        boolean fileCommited = headBlobsMap.containsKey(filename);
        // If the file is neither staged nor tracked by the head commit.
        if (!(fileCached || fileCommited)) {
            Utils.message("No reason to remove the file.");
            System.exit(0);
        }
        // If the file is staged.
        if (fileCached) {
            indexBlobsMap.remove(filename);
            Gitlet.setIndexMap(indexBlobsMap);
        }
        // If the file is in current commit.
        if (fileCommited) {
            Utils.restrictedDelete("./" + filename);
        }
    }

    /**
     * Display the log. Only follow parent1.
     * @param args args.length == 0
     */
    public static void log(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(0, args);

        String currentSHA1 = Gitlet.getHeadSHA1();
        Commit currentCommit = Gitlet.getHeadCommit();
        while (currentCommit != null) {
            CmdUtils.printCommit(currentSHA1);
            // Set parent
            currentSHA1 = currentCommit.getParentSHA1();
            currentCommit = Commit.readObject(currentSHA1);
        }
    }

    /**
     * Shwo the global-log,the order does not matter.
     */
    public static void globalLog(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(0, args);

        List<String> allCommitsSHA1 = Gitlet.getAllCommitsSHA1();
        for (String commitSHA1 : allCommitsSHA1) {
            CmdUtils.printCommit(commitSHA1);
        }
    }

    public static void find(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(1, args);
        String msg = args[0];

        boolean isFound = false;
        List<String> allCommitsSHA1 = Gitlet.getAllCommitsSHA1();
        for (String commitSHA1 : allCommitsSHA1) {
            Commit commit = Commit.readObject(commitSHA1);
            if (commit == null) {
                Utils.message("Will never be here.");
                System.exit(0);
            } else {
                if (msg.equals(commit.getMessage())) {
                    isFound = true;
                    System.out.println(commitSHA1);
                }
            }
        }
        if (!isFound) {
            Utils.message("Found no commit with that message.");
            System.exit(0);
        }
    }

    /**
     * Display the status.
     *  1. Branches.
     *  2. Staged. (in index, not in current commit or modified, whatever in working dir)
     *  3. Removed. (not in index, in current commit, whatever in working dir)
     *  4. Modifications not staged for commit.
     *      SHA1 is different in index and working dir
     *      In index but not in working dir
     *  5. Untracked Files. (In working directory not in index)
     * @param args args.length == 0
     */
    public static void status(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(0, args);

        /* Setup fileMap of {current commit, staging area(index), working directory} */
        // Get the blobMap of previous commit
        Commit headCommit = Gitlet.getHeadCommit();
        HashMap<String, String> headBlobsMap = headCommit.getBlobsMap();
        // Get the current index
        HashMap<String, String> indexBlobsMap = Gitlet.getIndexMap();
        // Get the current working-tree
        HashMap<String, String> fileMap = Gitlet.getFileMap();

        /* Setup the result list */
        List<String> stagedList = new LinkedList<String>();
        List<String> removedList = new LinkedList<String>();
        // key: filename value: true -> deleted, false -> modified
        List<AbstractMap.SimpleEntry<String, Boolean>> modifiedList =
                new LinkedList<AbstractMap.SimpleEntry<String, Boolean>>();
        List<String> untrackedList = new LinkedList<String>();

        /*
        Calc staged and modified
        Staged:
            exists in the index, not in current commit or modified.
        Modified:
            a filename is in index but not in working directory: (deleted)
            a file name is in index, and in working directory, but different SHA1: (modified)
         */
        indexBlobsMap.forEach((fileName, fileHash) -> {
            boolean inCommit = headBlobsMap.containsKey(fileName);
            if (!inCommit) {
                stagedList.add(fileName);
            } else {
                String commitHash = headBlobsMap.get(fileName);
                if (!commitHash.equals(fileHash)) {
                    stagedList.add(fileName);
                }
            }
            boolean nameInWorkingDir = fileMap.containsKey(fileName);
            if (!nameInWorkingDir) {
                modifiedList.add(new AbstractMap.SimpleEntry<String, Boolean>(fileName, true));
            } else {
                String workingDirHash = fileMap.get(fileName);
                if (!workingDirHash.equals(fileHash)) {
                    modifiedList.add(new AbstractMap.SimpleEntry<String, Boolean>(fileName, false));
                }
            }
        });

        /* Calc removed
           Files are in the current commit, but not in the index.
         */
        headBlobsMap.forEach((fileName, fileHash) -> {
            boolean nameInIndex = indexBlobsMap.containsKey(fileName);
            if (!nameInIndex) removedList.add(fileName);
        });

        /* calc untracked files
            Files are in working directory but not in index.
         */
        fileMap.forEach((fileName, fileHash) -> {
            boolean nameInIndex = indexBlobsMap.containsKey(fileName);
            if (!nameInIndex) untrackedList.add(fileName);
        });

        /* Sort the lists for display in lexicographic order */
        Collections.sort(stagedList);
        Collections.sort(removedList);
        modifiedList.sort((i, j) -> i.getKey().compareTo(j.getKey()));
        Collections.sort(untrackedList);

        /* Display Branches */
        List<String> branchList = Utils.plainFilenamesIn(Gitlet.BRANCHES_PATH);
        // Get the master branch, ignore detachd-head case for now.
        String headRef = Gitlet.getHeadRef();
        System.out.println("=== Branches ===");
        for (String branchName : branchList) {
            if (branchName.equals(headRef)) { System.out.print("*"); }
            System.out.println(branchName);
        }
        System.out.println();

        /* Display others */
        System.out.println("=== Staged Files ===");
        for (String filename : stagedList) System.out.println(filename);
        System.out.println();
        System.out.println("=== Removed Files ===");
        for (String filename : removedList) System.out.println(filename);
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        for (AbstractMap.SimpleEntry<String, Boolean> entry : modifiedList) {
            String status = entry.getValue() ? "(deleted)" : "(modified)";
            System.out.println(entry.getKey() + " " + status);
        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        for (String filename : untrackedList) System.out.println(filename);
    }

    /**
     * Checkout command:
     *  1. checkout -- [file name]
     *      overwrite the [file name] in the working dir with the version in head commit
     *      The new version of [file name] is NOT satged
     *      If [file name] doesn't exist in head commit, print "File does not exist in that commit."
     *  2. checkout [commit id] -- [file name]
     *      overwrite the [file name] in the working dir with the version in [commit id]
     *      The new version of [file name] is NOT satged
     *      If no such commit, print "No commit with that id exists."
     *      If file not exists, "print File does not exist in that commit."
     *  3. checkout [branch name]
     *      Overwrite the whole working dir with the version in [branch name] head.
     *      Clear the staging area
     *      If no such branch, print "No such branch exists."
     *      If the branch is the current branch, print "No need to checkout the current branch."
     *      If an untracked file will be overwritten by the checkout, print "There is an untracked file in the way; delete it or add it first."
     * @param args args.length = 1 || 2 || 3, [commit id] can be shortened
     */
    public static void checkout(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsRange(1, 4, args);

        /* Setup fileMaps(fileName -> SHA1) */
        HashMap<String, String> workingDirMap = Gitlet.getFileMap();
        HashMap<String, String> headBlobsMap = Gitlet.getHeadCommit().getBlobsMap();

        /* Case 1: checkout -- [file name] */
        if (args.length == 2 && args[0].equals("--")) {
            String fileName = args[1];
            boolean isFileInHead = headBlobsMap.containsKey(fileName);
            if (!isFileInHead) {
                Gitlet.printAndExit("File does not exist in that commit.");
            } else {
                String fileHash = headBlobsMap.get(fileName);
                Blob.writeBackFromGit(fileHash, fileName);
            }
        }

        /* Case 2: checkout [commit id] -- [file name] */
        if (args.length == 3 && args[1].equals("--")) {
            String fileHash = args[0], fileName = args[2];
            Commit historyCommit = Commit.readObject(fileHash);
            if (historyCommit == null) Gitlet.printAndExit("No commit with that id exists.");
            HashMap<String, String> commitBlobsMap = historyCommit.getBlobsMap();
            if (!commitBlobsMap.containsKey(fileName)) Gitlet.printAndExit("File does not exist in that commit.");
            String gitFileHash = commitBlobsMap.get(fileName);
            Blob.writeBackFromGit(gitFileHash, fileName);
        }

        /* Case 3: checkout [branch name] */
        if (args.length == 1) {
            String branchName = args[0];
            String headBranchName = Gitlet.getHeadRef();
            List<String> branchList = Gitlet.getBranchList();
            // i. branch is current head branch
            if (branchName.equals(headBranchName)) Gitlet.printAndExit("No need to checkout the current branch.");
            // ii. no such branch
            if (!branchList.contains(branchName)) Gitlet.printAndExit("No such branch exists.");
            // iii. have untracked file
            String branchHash = Gitlet.readBranchHash(branchName);
            Commit branchCommit = Commit.readObject(branchHash);
            HashMap<String, String> historyBlobsMap = branchCommit.getBlobsMap();
            if (Gitlet.hasUntrackedFile(historyBlobsMap)) Gitlet.printAndExit(" There is an untracked file in the way; delete it or add it first.");

            // normal case: overwrite the working directory.
            Gitlet.writeHEAD("/" + branchName);
            branchCommit.writeWorkingDir();
            // Clear the staging area, which is different from actual git
            Gitlet.setIndexMap(branchCommit.getBlobsMap());
        }
    }

    /**
     * Create a new branch, but not immediately switch to it
     * If the branch already exists, print "A branch with that name already exists."
     * @param args args.length() == 1
     */
    public static void branch(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(1, args);
        String branchName = args[0];

        /* Branch name exists */
        List<String> branchList = Gitlet.getBranchList();
        if (branchList.contains(branchName)) {
            Utils.message("A branch with that name already exists.");
            System.exit(0);
        }
        String currentSHA1 = Gitlet.getHeadSHA1();
        Gitlet.writeBranch(branchName, currentSHA1);
    }

    /**
     * Remove the branch of given name.
     * If no such branch, print "A branch with that name does not exist."
     * If trying to remove the current branch, print "Cannot remove the current branch."
     * @param args length == 1
     */
    public static void rmBranch(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(1, args);
        String branchName = args[0];

        String currentBranchName = Gitlet.getHeadRef();
        List<String> branchList = Gitlet.getBranchList();
        // Want to delete current branch
        if (branchName.equals(currentBranchName)) {
            Utils.message("Cannot remove the current branch.");
            System.exit(0);
        }
        // No such branch.
        if (!branchList.contains(branchName)) {
            Utils.message("A branch with that name does not exist.");
            System.exit(0);
        }
        // Normal case.
        Utils.restrictedDelete(Gitlet.BRANCHES_PATH + branchName);
    }

    public static void reset(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(1, args);
        String commitID = args[0];

        // check if such commit exists
        commitID = Commit.abbrevSHA1toFull(commitID);
        Commit targetCommit = Commit.readObject(commitID);
        if (targetCommit == null) {
            Utils.message("No commit with that id exists.");
            System.exit(0);
        }
        // check untrackeed file
        Commit headCommit = Gitlet.getHeadCommit();
        if (Gitlet.hasUntrackedFile(headCommit.getBlobsMap())) {
            Utils.message("There is an untracked file in the way; delete it or add it first.");
            System.exit(0);
        }
        // Change working dir, index, head
        targetCommit.writeWorkingDir();
        Gitlet.setIndexMap(targetCommit.getBlobsMap());
        Gitlet.writeHEAD(commitID);
    }

    /**
     * Suppose head in master, want to merge branch B
     * Cases:
     *  1. If split point is B's commit, do nothing, print "Given branch is an ancestor of the current branch."
     *  2. If split point is current branch, set current branch to B and print "Current branch fast-forwarded."
     *  3. File modified in B since split, not modified in master, should be staged.
     *  4. File modified in master since split, but not in B, should be staged also.
     *  5. File in master and B modified in same way, do nothing.
     *  6. New file only in master branch should remain
     *  7. New file only in B branch should be checkout and staged
     *  8. File unmodified in master, but absent in B should be removed and untracked.
     *  9. File unmodified in B, but absent in master should remain absent.
     *  10. File modified in different way in B and master is a conflict.
     *      i. change in different way
     *      ii. one change, one deleted
     *      iii. absent at split point, different in two branches
     *
     * Merge commit with message "Merged [given branch name] into [current branch name]."
     * If there is a conflict, print "Encountered a merge conflict."
     * For multiple split point, choose the one nearest to current branch.
     * If there are severl split points with same distance, choose randomly.
     *
     * Error cases:
     *  1. If there is staged addition or removals, print "You have uncommitted changes."
     *  2. If a branch with given name not exist, print "A branch with that name does not exist."
     *  3. If attempting to merge into itself, print "Cannot merge a branch with itself."
     *  4. If no change, let commit error message handle it.
     *  5. If the working dir has untracked file
     *    , print "There is an untracked file in the way; delete it or add it first."
     * @param args length == 1, [branch name]
     */
    public static void merge(String... args) {
        // Check if .gitlet is initialized
        CmdUtils.checkRep();
        // Check the args length
        CmdUtils.checkArgsNum(1, args);
        String givenBranch = args[0];

        /* init current maps, and other datas */
        HashMap<String, String> workingDirMap = Gitlet.getFileMap();
        HashMap<String, String> headBlobsMap = Gitlet.getHeadCommit().getBlobsMap();
        HashMap<String, String> indexBlobsMap = Gitlet.getIndexMap();
        List<String> branchList = Gitlet.getBranchList();
        String currentBranch = Gitlet.getHeadRef();
        String currentBranchHash = Gitlet.getHeadSHA1();
        String givenBranchHash = Gitlet.readBranchHash(givenBranch);
        Commit givenBrCommit = Commit.readObject(givenBranchHash);
        HashMap<String, String> givenBrBlobsMap = givenBrCommit.getBlobsMap();

        /* check error cases */
        // 1. something staged but not commited
        boolean allCommited = headBlobsMap.equals(indexBlobsMap);
        if (!allCommited) Gitlet.printAndExit("You have uncommitted changes.");
        // 2. input branch not exist
        boolean branchExists = branchList.contains(givenBranch);
        if (!branchExists) Gitlet.printAndExit("A branch with that name does not exist.");
        // 3. merge into itself
        if (givenBranch.equals(currentBranch)) Gitlet.printAndExit("Cannot merge a branch with itself.");
        // 4. Nochange: implement inside calling commit
        // 5. has untracked file
        workingDirMap.forEach((filenName, fileHash) -> {
            boolean fileTracked = indexBlobsMap.containsKey(filenName);
            if (!fileTracked) Gitlet.printAndExit("There is an untracked file in the way; delete it or add it first.");
        });

        /* Find the split point */
        // Ordered list of all ancestors of head and given branch. (for multiple parents, it is BFS ordered)
        List<String> ancestorsOfHead = Gitlet.getAllAncestors(Gitlet.getHeadSHA1());
        List<String> ancestorsOfGivenBr = Gitlet.getAllAncestors(givenBranchHash);
        Set<String> ancestorOfGivenBrSet = new HashSet<>(ancestorsOfGivenBr);
        // Find the split point: Iterate the ancestorsOfHead, find one in ancestorsOfGivenBr
        String splitPointHash = null;
        for (String currentHash : ancestorsOfHead) {
            if (ancestorOfGivenBrSet.contains(currentHash)) {
                //split point found!
                splitPointHash = currentHash;
                break;
            }
        }
        if (splitPointHash == null) {
            // Should never be here
            Utils.error("Find split point error");
        }
        // Case1: split point is given branch's commit
        if (splitPointHash == givenBranchHash) Gitlet.printAndExit("Given branch is an ancestor of the current branch.");
        // Case2: Split point is current branch
        if (splitPointHash == Gitlet.getHeadSHA1()) {
            //set curent branch to given branch
            Gitlet.writeBranch(currentBranch, givenBranchHash);
            Gitlet.printAndExit("Current branch fast-forwarded.");
        }
        Commit splitPointCommit = Commit.readObject(splitPointHash);

        /* Get three maps of filename -> BlobHash: prev(split point), current, given branch
            Build a new index, write to .gitlet, and call commitHelper
         */
        HashMap<String, String> prevBlobsMap = splitPointCommit.getBlobsMap();
        // deep copy the head commit's blobMap
        HashMap<String, String> newIndex = new HashMap<>(headBlobsMap);
        // Case 3, 4, 5, 8, 9, 10.i
        prevBlobsMap.forEach((prevName, prevHash) -> {
            boolean inGivenBr = givenBrBlobsMap.containsKey(prevName);
            boolean inCurrent = headBlobsMap.containsKey(prevName);
            String givenBrHash = givenBrBlobsMap.get(prevName);
            String currentHash = headBlobsMap.get(prevName);
            // Case 3, 4, 5
            if (inCurrent && inGivenBr) {
                boolean modifiedInBr = (!givenBrHash.equals(prevHash));
                boolean modifiedInCurrent = (!currentHash.equals(prevHash));
                if (modifiedInBr && !modifiedInCurrent) {
                    newIndex.put(prevName, givenBrHash);
                }
                if (modifiedInBr && modifiedInCurrent) {
                    if (givenBrHash.equals(currentHash)) {
                        // modified in same way: do nothing
                    }
                    // Case 10.i
                    else {
                        String headContent = Blob.readFile(currentHash);
                        String givenBrContent = Blob.readFile(givenBranchHash);
                        String newContent = "<<<<<<< HEAD\n" + headContent + "=======" + givenBrContent + ">>>>>>>\n";
                        String newContentHash = CmdUtils.writeConflictedMergeFile(prevName, newContent);
                        // Update index
                        newIndex.put(prevName, newContentHash);
                        // Print conflict message
                        Utils.message("Encountered a merge conflict.");
                    }
                }
            }
            // Case 8
            if (inCurrent && !inGivenBr) {
                if (prevHash.equals(currentHash)) {
                    newIndex.remove(prevName);
                } // Case 10.ii
                else {
                    String headContent = Blob.readFile(currentHash);
                    String newContent = "<<<<<<< HEAD\n" + headContent + "=======" + ">>>>>>>\n";
                    String newContentHash = CmdUtils.writeConflictedMergeFile(prevName, newContent);
                    // Update index
                    newIndex.put(prevName, newContentHash);
                }
            }
            // Case 9
            if (!inCurrent && inGivenBr) {
                // 9: do nothing
                // Case 10.ii
                if (!prevHash.equals(givenBranchHash)) {
                    String givenBrContent = Blob.readFile(givenBranchHash);
                    String newContent = "<<<<<<< HEAD\n" + "=======" + givenBrContent +  ">>>>>>>\n";
                    String newContentHash = CmdUtils.writeConflictedMergeFile(prevName, newContent);
                    // Update index
                    newIndex.put(prevName, newContentHash);
                    // Print conflict message
                    Utils.message("Encountered a merge conflict.");
                }
            }
        });
        // Case 6
        headBlobsMap.forEach((currentName, currentHash) -> {
            boolean inSplitPoint = prevBlobsMap.containsKey(currentName);
            boolean inGivenBranch = givenBrBlobsMap.containsKey(currentName);
            // Case 6
            if (!inSplitPoint && !inGivenBranch) {
                // do notiong to remain the file.
            }
            // Case 10.3
            if (!inSplitPoint && inGivenBranch) {
                String givenBrHash = givenBrBlobsMap.get(currentName);
                String givenBrContent = Blob.readFile(givenBranchHash);
                String headContent = Blob.readFile(currentHash);
                String newContent = "<<<<<<< HEAD\n" + headContent + "=======" + givenBrContent +  ">>>>>>>\n";
                // Write the newContent to working directory
                String newContentHash = CmdUtils.writeConflictedMergeFile(currentName, newContent);
                // Update index
                newIndex.put(currentName, newContentHash);
                // Print conflict message
                Utils.message("Encountered a merge conflict.");
            }
        });
        // Case 7
        givenBrBlobsMap.forEach((brName, brHash) -> {
            boolean inSplitPoint = prevBlobsMap.containsKey(brName);
            boolean inCurrentBranch = headBlobsMap.containsKey(brName);
            // 7
            if (!inSplitPoint && !inCurrentBranch) {
                // checkout
                Blob.writeBackFromGit(brHash, brName);
                // add to index
                newIndex.put(brName, brHash);
                // Print conflict message
                Utils.message("Encountered a merge conflict.");
            }
        });

        /* Write new indexfile to .gitlet, and commit */
        Gitlet.setIndexMap(newIndex);
        String commitMessage = "Merged " + givenBranch + " into " + currentBranch + ".";
        CmdUtils.commitHelper(commitMessage, currentBranchHash, givenBranchHash);
    }
}
