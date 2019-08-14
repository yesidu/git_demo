package git_demo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.alibaba.fastjson.JSONObject;

public class GitUtil {
    private static Logger logger = LoggerFactory.getLogger(GitUtil.class);

    /** 远程仓库url **/
    private String remoteUrl;
    /** 本地仓库路径 **/
    private String localPath;
    /** 本地仓库localPath/.git **/
    private final String localRepoGitConfig;
    private File localFile;
    /** 验证provider **/
    private UsernamePasswordCredentialsProvider credentialsProvider;

    public GitUtil(String remoteUrl, String localPath) {
        Assert.notNull(remoteUrl, "远程仓库url不能为空");
        Assert.notNull(localPath, "本地仓库路径不能为空");
        File targetFile = new File(localPath);
        if (!targetFile.exists()) {
            File fileParent = targetFile.getParentFile();
            if (!fileParent.exists()) {
                fileParent.mkdirs();
            }
            targetFile.mkdir();
        }
        if (!targetFile.isDirectory()) {
            throw new RuntimeException("本地仓库必须是目录：" + localPath);
        }

        this.remoteUrl = remoteUrl;
        this.localPath = localPath;
        this.localRepoGitConfig = localPath + "/.git";

        credentialsProvider = new UsernamePasswordCredentialsProvider("635915376@qq.com", "dwf206711");
        localFile = new File(localPath);
    }

    private Git getGit() throws IOException {
        Git git = Git.open(localFile);
        return git;
    }

    public static void closeGit(Git git) {
        try {
            if (git != null) {
                git.close();
            }
        } catch (Exception e) {
            logger.error("close git error", e);
        }
    }

    /**
     * clone一个远程仓库
     */
    public boolean cloneRepository() {
        String logId = UUID.randomUUID().toString().replace("-", "");
        String logMsg = "远程仓库：" + remoteUrl + "，本地仓库：" + localPath + "，logId：" + logId;
        logger.info("[clone项目]开始：{}", logId);
        logger.info("[clone项目]{}", logMsg);
        try {
            File localFile = new File(localPath);
            if (!localFile.isDirectory()) {
                logger.info("[clone项目]本地仓库必须是目录：{}", logId);
                return false;
            }
            String[] listFiles = localFile.list();
            if (listFiles != null && listFiles.length > 0) {
                logger.info("[clone项目]本地仓库必须是一个空目录：{}", logId);
                return false;
            }
            CloneCommand cc = Git.cloneRepository().setURI(remoteUrl);
            cc.setDirectory(localFile).call();

            logger.info("[clone项目]成功：{}", logId);
            return true;
        } catch (Exception e) {
            logger.info("[clone项目]出现异常：{}", logId, e);
            return false;
        } finally {
            logger.info("[clone项目]结束：{}", logId);
        }
    }

    /**
     * 获取本地分支
     */
    public List<String> getLocalBranch() {
        /*try {
            Git git = Git.open(new File(localRepoGitConfig));
            Map<String, Ref> refs = git.getRepository().getAllRefs();
            for (Entry<String, Ref> entry : refs.entrySet()) {
                System.out.println(entry.getKey() + " : " + entry.getValue());
            }
        } catch (Exception e) {
        }*/
        return getBranch(null);
    }

    /**
     * 获取远程分支
     */
    public List<String> getRemoteBranch() {
        try {
            Git git = Git.open(new File(localPath));
            List<RemoteConfig> list = git.remoteList().call();
            for (RemoteConfig remoteConfig : list) {
                System.out.println(remoteConfig.getName());
            }
        } catch (Exception e) {
        }
        return getBranch(ListMode.REMOTE);
    }

    /**
     * 获取本地分支 + 远程分支
     */
    public List<String> getAllBranch() {
        return getBranch(ListMode.ALL);
    }

    private List<String> getBranch(ListMode mode) {
        List<String> list = new ArrayList<String>();
        try {
            pull();
            Git git = Git.open(new File(localRepoGitConfig));
            ListBranchCommand command = git.branchList();
            if (mode != null) {
                command.setListMode(mode);
            }
            List<Ref> refs = command.call();
            for (Ref ref : refs) {
                list.add(ref.getName());
            }
        } catch (Exception e) {
            logger.info("获取分支异常，ListMode：{}", mode, e);
        }
        return list;
    }

    /**
     * 判断本地分支是否存在
     */
    public boolean isExistLocalBranch(String branchName) {
        List<String> localBranchs = getLocalBranch();
        for (String localBranch : localBranchs) {
            if (localBranch.endsWith("/" + branchName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断远程分支是否存在
     */
    public boolean isExistRemoteBranch(String branchName) {
        List<String> localBranchs = getRemoteBranch();
        for (String localBranch : localBranchs) {
            if (localBranch.endsWith("/" + branchName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除本地分支
     */
    public boolean deleteLocalBranch(String branchName) {
        try {
            Git git = Git.open(new File(localRepoGitConfig));
            List<Ref> refs = git.branchList().call();
            String newBranchIndex = "refs/heads/" + branchName;
            for (Ref ref : refs) {
                if (ref.getName().endsWith(newBranchIndex)) {
                    logger.info("删除本地分支：{}", ref.getName());
                    // git.branchDelete().setBranchNames(branchName).setForce(true).call();
                    git.branchDelete().setBranchNames(newBranchIndex).setForce(true).call();
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            logger.info("删除本地分支异常：{}", branchName, e);
            return false;
        }
    }

    /**
     * 删除远程分支
     */
    public boolean deleteRemoteBranch(String branchName) {
        try {
            Git git = Git.open(new File(localRepoGitConfig));
            List<Ref> refs = git.branchList().setListMode(ListMode.REMOTE).call();
            for (Ref ref : refs) {
                if (ref.getName().endsWith("/" + branchName)) {
                    logger.info("删除远端分支：{}", ref.getName());
                    RefSpec refSpec = new RefSpec()
                            .setSource(null)
                            .setDestination("refs/heads/" + branchName);
                    git.push().setCredentialsProvider(credentialsProvider).setRefSpecs(refSpec).setRemote("origin").call();
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            logger.info("删除远端分支异常：{}", branchName, e);
            return false;
        }
    }

    /**
     * 新建分支 TODO
     */
    public boolean newBranch(String branchName) {
        String logId = UUID.randomUUID().toString().replace("-", "");
        String newBranchIndex = "/" + branchName;
        try {
            Git git = Git.open(new File(localRepoGitConfig));
            List<Ref> refs = git.branchList().setListMode(ListMode.ALL).call();
            for (Ref ref : refs) {
                if (ref.getName().endsWith(newBranchIndex)) {
                    logger.info("[新建分支]分支已经存在：{}", ref.getName());
                    return false;
                }
            }
            //新建分支
            logger.info("[新建分支]创建本地分支：{}", branchName);
            Ref ref = git.branchCreate().setName(branchName).call();
            // 推送到远程
            logger.info("[新建分支]推送到远程：{}", branchName);
            git.push().setCredentialsProvider(credentialsProvider).add(ref).call();
            return true;
        } catch (Exception e) {
            logger.info("[新建分支]出现异常：{}", logId, e);
            return false;
        } finally {
            logger.info("[新建分支]结束：{}", logId);
        }
    }

    /**
     * 显示状态
     */
    public Status status(boolean showLog) {
        try {
            Git git = Git.open(new File(localRepoGitConfig));
            Status status = git.status().call();
            if (showLog) {
                logger.info("检测状态：{}", JSONObject.toJSONString(status));
                logger.info("当前状态，clean：{}，hasUncommittedChanges：{}", status.isClean(), status.hasUncommittedChanges());
                showStatusInfo("Git Change: {}", status.getChanged());
                showStatusInfo("Git Modified: {}", status.getModified());
                showStatusInfo("Git UncommittedChanges: {}", status.getUncommittedChanges());
                showStatusInfo("Git Untracked: {}", status.getUntracked()); // Untracked：新文件还没add
                showStatusInfo("Git UntrackedFolders：{}", status.getUntrackedFolders());
                showStatusInfo("Git Conflicting：{}", status.getConflicting());
                showStatusInfo("Git Missing：{}", status.getMissing());
                showStatusInfo("Git added：{}", status.getAdded());
            }
            return status;
        } catch (Exception e) {
            logger.info("显示状态异常", e);
            return null;
        }
    }

    public Status showStatus() {
        return status(true);
    }

    private void showStatusInfo(String text, Set<String> info) {
        if (!CollectionUtils.isEmpty(info)) {
            logger.info(text, info);
        }
    }

    public void showLog() {
        try {
            Git git = Git.open(new File(localRepoGitConfig));
            Iterable<RevCommit> list = git.log().call();
            list.forEach(rev -> {
                System.out.println(rev.getName() + " " + rev.getFullMessage());
            });
        } catch (Exception e) {
            logger.error("log 异常", e);
        }
    }

    public boolean isClean() {
        Status status = showStatus();
        return status != null && status.isClean();
    }

    /**
     * 检出分支
     */
    public boolean checkout(String branchName) {
        logger.info("准备checkout：{}", branchName);

        try {
            if (!isClean()) {
                logger.info("checkout[{}]失败，当前分支有未提交数据", branchName);
                return false;
            }
            if (!isExistLocalBranch(branchName)) {
                logger.info("checkout[{}]失败，分支不存在", branchName);
                return false;
            }
            Git git = Git.open(new File(localRepoGitConfig));
            git.checkout().setName(branchName).call();
            pull();
            return true;
        } catch (Exception e) {
            logger.error("checkout[{}]失败", branchName, e);
            return false;
        }
    }

    public void pull() {
        try {
            logger.info("正在pull...");
            Git git = Git.open(new File(localRepoGitConfig));
            git.pull().call();
            git.close();
        } catch (Exception e) {
            logger.error("pull 异常", e);
        }
    }

    public void fetch() {
        try {
            logger.info("正在fetch...");
            Git git = Git.open(new File(localRepoGitConfig));
            git.fetch().call();
            git.close();
        } catch (Exception e) {
            logger.error("fetch 异常", e);
        }
    }

    public boolean push() {
        try {
            logger.info("正在push...");
            Git git = Git.open(new File(localRepoGitConfig));
            PushCommand push = git.push();
            push.setCredentialsProvider(credentialsProvider).setForce(true);
            Iterable<PushResult> it = push.call();
            for (PushResult pushResult : it) {
                System.out.println(pushResult.getMessages());
            }
            return true;
        } catch (Exception e) {
            logger.info("push 异常", e);
            return false;
        }
    }

    public boolean commit(String commitMsg, boolean allowEmpty) {
        try {
            if(commitMsg == null) {
                commitMsg = "Does not has any commit !";
            }
            logger.info("开始提交分支：{}", commitMsg);
            Git git = getGit();
            Status status = showStatus();
            if (!allowEmpty && isClean()) {
                logger.info("当前没有需要提交的...");
                return true;
            }
            if (!CollectionUtils.isEmpty(status.getConflicting())) {
                logger.info("提交失败，冲突还没解决：{}", status.getConflicting());
                return false;
            }
            Set<String> untrackedSet = status.getUntracked();
            if (!CollectionUtils.isEmpty(untrackedSet)) {
                logger.info("准备add新文件：{}", untrackedSet);
                AddCommand add = git.add();
                for (String filepattern : untrackedSet) {
                    add.addFilepattern(filepattern);
                }
                add.call();
            }
            CommitCommand commit = git.commit();
            commit.setMessage(commitMsg);
            commit.setAllowEmpty(true);
            commit.call();
            closeGit(git);
            logger.info("提交成功：{}", commitMsg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String currentBranch() {
        try {
            Git git = Git.open(new File(localRepoGitConfig));
            String branch = git.getRepository().getBranch();
            logger.info("当前本地分支：{}", branch);
            return branch;
        } catch (Exception e) {
            logger.info("获取当前本地分支异常", e);
        }
        return null;
    }

    /**
     * TODO
     */
    public String currentRefRemoteBranch() {
        try {
            Git git = Git.open(new File(localRepoGitConfig));
            StoredConfig config = git.getRepository().getConfig();
            config.getSections();
            System.out.println(JSONObject.toJSONString(config.getSections()));
            for (String session : config.getSections()) {
                System.out.println(JSONObject.toJSONString(config.getNames(session)));
            }
            String branch = config.getString("branch", currentBranch(), "remote");
            logger.info("当前远程分支：{}", branch);
            return branch;
        } catch (Exception e) {

        }
        return null;
    }

    public static void main(String[] args) {
        String localPath = "C:/Users/yesido/git/demo";
        String remoteUrl = "https://github.com/yesidu/demo.git";

        GitUtil util = new GitUtil(remoteUrl, localPath);
        // util.showStatus();
        // util.cloneRepository();
        // String branchName = "dev_dwf";
        // util.newBranch(branchName);
        // util.deleteLocalBranch(branchName);
        // util.deleteRemoteBranch(branchName);
        // util.checkout("test");
        // util.pull();
        // util.showLog();
        util.showStatus();
        util.currentBranch();
        // util.currentRefRemoteBranch();
        // util.getRemoteBranch();
        // util.push();
        // util.commit("empty commit", false);

    }
}
