package com.teamboid.twitter.cab;

import java.util.ArrayList;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.*;

import com.teamboid.twitter.Account;
import com.teamboid.twitter.ComposerScreen;
import com.teamboid.twitter.R;
import com.teamboid.twitter.UserListActivity;
import com.teamboid.twitter.TabsAdapter.BaseListFragment;
import com.teamboid.twitter.listadapters.SearchUsersListAdapter;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitterapi.client.Twitter;
import com.teamboid.twitterapi.user.FollowingType;
import com.teamboid.twitterapi.user.User;

/**
 * The contextual action bar for any lists/columns that display twitter4j.User objects.
 *
 * @author Aidan Follestad
 */
public class UserListCAB {

    public static Activity context;

    public static User[] getSelectedUsers() {
        ArrayList<User> toReturn = new ArrayList<User>();
        if (context instanceof UserListActivity) {
            UserListActivity activity = (UserListActivity) context;
            SparseBooleanArray checkedItems = activity.getListView().getCheckedItemPositions();
            if (checkedItems != null) {
                for (int i = 0; i < checkedItems.size(); i++) {
                    if (checkedItems.valueAt(i)) {
                        toReturn.add((User) activity.binder.getItem(checkedItems.keyAt(i)));
                    }
                }
            }
        } else {
            for (int i = 0; i < context.getActionBar().getTabCount(); i++) {
                Fragment frag = context.getFragmentManager().findFragmentByTag("page:" + Integer.toString(i));
                if (frag instanceof BaseListFragment) {
                    User[] toAdd = ((BaseListFragment) frag).getSelectedUsers();
                    if (toAdd != null && toAdd.length > 0) {
                        for (User u : toAdd) toReturn.add(u);
                    }
                }
            }
        }
        return toReturn.toArray(new User[0]);
    }

    public static void reinsertUser(User user) {
        if (context instanceof UserListActivity) {
            ListAdapter adapter = ((UserListActivity) context).getListView().getAdapter();
            ((SearchUsersListAdapter) adapter).update(user);
        } else {
            for (int i = 0; i < context.getActionBar().getTabCount(); i++) {
                Fragment frag = context.getFragmentManager().findFragmentByTag("page:" + Integer.toString(i));
                if (frag instanceof BaseListFragment) {
                    ListAdapter adapter = ((BaseListFragment) frag).getListView().getAdapter();
                    if (adapter instanceof SearchUsersListAdapter) {
                        ((SearchUsersListAdapter) adapter).update(user);
                    }
                }
            }
        }
    }


    public static final AbsListView.MultiChoiceModeListener choiceListener = new AbsListView.MultiChoiceModeListener() {

        private void updateTitle(int selectedUsersLength) {
            if (selectedUsersLength == 1) {
                actionMode.setTitle(R.string.one_user_selected);
            } else {
                actionMode.setTitle(context.getString(R.string.x_users_selected).replace("{X}", Integer.toString(selectedUsersLength)));
            }
        }

        private void updateMenuItems(final User[] selUsers, Menu menu) {
            final MenuItem follow = menu.findItem(R.id.followAction);
            follow.setEnabled(false);
            final Account acc = AccountService.getCurrentAccount();
            if (selUsers.length == 1 && selUsers[0].getId() == acc.getId()) {
                follow.setTitle(R.string.this_is_you);
                return;
            }
            follow.setTitle(R.string.loading_str);
            new Thread(new Runnable() {
                public void run() {
                    for (int i = 0; i < selUsers.length; i++) {
                        if (selUsers[i].getId() == acc.getId() ||
                                selUsers[i].getFollowingType() != FollowingType.UNKNOWN) {
                            continue;
                        }
                        try {
                            boolean isFollowing = acc.getClient().existsFriendship(acc.getUser().getScreenName(), selUsers[i].getScreenName());
                            if (isFollowing) {
                                selUsers[i].setFollowingType(FollowingType.FOLLOWING);
                            } else selUsers[i].setFollowingType(FollowingType.NOT_FOLLOWING);
                        } catch (final Exception e) {
                            e.printStackTrace();
                            final User curUser = selUsers[i];
                            context.runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(context, context.getString(R.string.failed_check_following).replace("{user}", curUser.getScreenName()), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                    context.runOnUiThread(new Runnable() {
                        public void run() {
                            boolean allFollowing = true;
                            for (User u : selUsers) {
                                if (u.getId() == acc.getId()) continue;
                                UserListCAB.reinsertUser(u);
                                if (u.getFollowingType() == FollowingType.NOT_FOLLOWING) {
                                    allFollowing = false;
                                }
                            }
                            if (allFollowing) {
                                follow.setTitle(R.string.unfollow_str);
                            } else follow.setTitle(R.string.follow_str);
                            follow.setEnabled(true);
                        }
                    });
                }
            }).start();
        }

        private ActionMode actionMode;

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int i, long l, boolean b) {
            User[] selUsers = getSelectedUsers();
            updateTitle(selUsers.length);
            updateMenuItems(selUsers, mode.getMenu());
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.user_cab, menu);
            actionMode = mode;
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) { return false; }

        @Override
        public boolean onActionItemClicked(ActionMode mode, final MenuItem item) {
            final User[] selUsers = getSelectedUsers();
            mode.finish();

            switch (item.getItemId()) {
                case R.id.mentionAction: {
                    String mentionStr = "";
                    for (User user : selUsers) {
                        mentionStr += "@" + user.getScreenName() + " ";
                    }
                    context.startActivity(new Intent(context, ComposerScreen.class)
                            .putExtra("append", mentionStr.trim()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    return true;
                }
                case R.id.followAction: {
                    item.setEnabled(false);
                    final Twitter cl = AccountService.getCurrentAccount().getClient();
                    if (item.getTitle().equals(context.getString(R.string.follow_str))) {
                        new Thread(new Runnable() {
                            public void run() {
                                for (final User user : selUsers) {
                                    try {
                                        cl.createFriendship(user.getId());
                                        user.setFollowingType(FollowingType.FOLLOWING);
                                        context.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() { UserListCAB.reinsertUser(user); }
                                        });
                                    } catch (final Exception e) {
                                        e.printStackTrace();
                                        context.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(context, context.getString(R.string.failed_follow_str).replace("{user}", user.getScreenName()), Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }
                                context.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        item.setEnabled(true);
                                        item.setTitle(R.string.unfollow_str);
                                    }
                                });
                            }
                        }).start();
                    } else {
                        new Thread(new Runnable() {
                            public void run() {
                                for (final User user : selUsers) {
                                    try {
                                        cl.destroyFriendship(user.getId());
                                        user.setFollowingType(FollowingType.NOT_FOLLOWING);
                                        context.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() { UserListCAB.reinsertUser(user); }
                                        });
                                    } catch (final Exception e) {
                                        e.printStackTrace();
                                        context.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(context, context.getString(R.string.failed_unfollow_str).replace("{user}", user.getScreenName()), Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }
                                context.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        item.setEnabled(true);
                                        item.setTitle(R.string.follow_str);
                                    }
                                });
                            }
                        }).start();
                    }
                    return true;
                }
                case R.id.shareAction: {
                    String shareStr = "";
                    for (int i = 0; i < selUsers.length; i++) {
                        String name = selUsers[i].getScreenName();
                        if (i > 0) shareStr += "\n";
                        shareStr += "@" + name + " (https://twitter.com/" + name + ")";
                    }
                    context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareStr),
                            context.getString(R.string.share_str)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    return true;
                }
                default: {
                    return false;
                }
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) { }
    };
}