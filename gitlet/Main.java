package gitlet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     * init Create a new Gitlet in current directory.
     * add [file name] Add a copy of the file to the staging area.
     * commit [message] Save certain files in the current commit.
     * rm [filename] Unstage the file if it is currently staged.
     * log Display info of commits from current to initial commit.
     * global-log Displa info about all commits ever made.
     * find [commit message] Prints out the ids of all commits that have the given commit message.
     * status Display what branches currently exist, and mark the current branch with *.
     * checkout
     *      1. -- [file name]
     *      2. [commit id] -- [file name]
     *      3. [branch name]
     *  branch [branch name]
     *  rm-branch [branch name]
     *  reset [commit id]
     *  merge [branch name]
     *  rebase [branch name]
     *
     * <COMMAND> <OPERAND> .... */
    public static void main(String... args) {
        /**
         * Requirements:
         * 1. If a user doesn’t input any arguments, print the message Please enter a command. and
         * exit.
         * 2. If a user inputs a command that doesn’t exist, print the message No command with that
         * name exists. and exit.
         * 3. If a user inputs a command with the wrong number or format of operands, print the
         * message Incorrect operands. and exit.
         * 4. If a user inputs a command that requires being in an initialized Gitlet working
         * directory (i.e., one containing a .gitlet subdirectory), but is not in such a directory, print the message Not in an initialized Gitlet directory.
         */
        // Requirement 1
        if (args.length == 0) {
            Utils.message("Please enter a command.");
            System.exit(0);
        }
        // Get operands for gitlet commands.
        // Requirements 3 and 4 are implemented in Cmd.java
        int operandsCount = args.length - 1;
        String[] operands = new String[operandsCount];
        System.arraycopy(args, 1, operands, 0, operandsCount);
        String cmd = args[0];
        if (cmd.equals("global-log")) cmd = "globalLog";
        if (cmd.equals("rm-branch")) cmd = "rmBranch";
        try {
            Method m = Cmd.class.getMethod(cmd, String[].class);
            m.invoke(null, (Object) operands);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Utils.message("No command with that name exists.");
            System.exit(0);
        }
    }

}
