/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   Contributors:
 *                Bademus
 */

package org.github.bademux.feedly.andrss;

import com.google.api.client.http.HttpResponseException;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.github.bademux.feedly.andrss.helpers.ProcessDialogAsyncTask;
import org.github.bademux.feedly.api.model.Profile;
import org.github.bademux.feedly.api.oauth2.FeedlyCredential;
import org.github.bademux.feedly.api.util.FeedlyUtil;
import org.github.bademux.feedly.api.util.FeedlyWebAuthActivity;
import org.github.bademux.feedly.api.util.db.BackgroundQueryHandler;
import org.github.bademux.feedly.service.FeedlyBroadcastReceiver;
import org.github.bademux.feedly.service.FeedlyCacheService;

import java.io.IOException;

import static org.github.bademux.feedly.api.service.ServiceManager.ACTION_INIT;
import static org.github.bademux.feedly.api.util.FeedlyWebAuthActivity.getResponceUrl;

public class MainActivity extends Activity
    implements NavigationFragment.OnFragmentInteractionListener,
               AuthInfoFragment.OnFragmentInteractionListener,
               ContentFragment.OnFragmentInteractionListener {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    try {
      mFeedlyUtil = new FeedlyUtil(this, getString(R.string.client_id),
                                   getString(R.string.client_secret));
    } catch (IOException e) {
      Log.e(TAG, "Something goes wrong", e);
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    }

    mQueryHandler = new BackgroundQueryHandler(getContentResolver());

    setContentView(R.layout.activity_main);

    initNavigationDrawer();

    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    if (sp.getBoolean(KEY_PREFS_FIRST_LAUNCH, true)) {
      sp.edit().putBoolean(KEY_PREFS_FIRST_LAUNCH, false).commit();
      Log.d(TAG, "First run");
      sendBroadcast(new Intent(ACTION_INIT, null, this, FeedlyBroadcastReceiver.class));
    }

    mListFragment = new ContentFragment();
    commitFragment();
  }

  private void initNavigationDrawer() {
    mTitle = getTitle();

    // Set up the drawer.
    mNavigationFragment = (NavigationFragment)
        getFragmentManager().findFragmentById(R.id.navigation_drawer);
    DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
    mNavigationFragment.setUp(R.id.navigation_drawer, drawerLayout);
  }

  @Override
  public void onGroupSelected(String groupUrl) {
    mListFragment.startQueryOnCategory(groupUrl);
  }

  @Override
  public void onChildSelected(String childUrl) {
    mListFragment.startQueryOnFeed(childUrl);
  }

  protected void commitFragment() {
    FragmentTransaction transaction = getFragmentManager().beginTransaction();
    transaction.replace(R.id.container, isAuthenticated() ? mListFragment : new AuthInfoFragment());
    transaction.commit();
  }

  @Override
  public boolean isAuthenticated() { return mFeedlyUtil.isAuthenticated(); }

  @Override
  public void onLogin() {
    if (mFeedlyUtil.isAuthenticated()) {
      Toast.makeText(this, "Already logged in", Toast.LENGTH_SHORT).show();
      return;
    }

    try {
      FeedlyWebAuthActivity.startActivityForResult(this, mFeedlyUtil.getRequestUrl());
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onLogout() {
    if (!mFeedlyUtil.isAuthenticated()) {
      Toast.makeText(this, "Already logged out", Toast.LENGTH_SHORT).show();
      return;
    }

    new ProcessDialogAsyncTask(this) {
      @Override
      protected void doInBackground() {
        try {
          mFeedlyUtil.logout();
          commitFragment();
          toast(R.string.msg_signed_out);
        } catch (Exception e) {
          toast((e instanceof HttpResponseException) ?
                FeedlyUtil.getErrorMessage((HttpResponseException) e) : e.getMessage());
          Log.e(TAG, "Something goes wrong", e);
        }
      }
    }.execute();
  }

  @Override
  public void onRefreshList() { onRefresh(FeedlyCacheService.ACTION_FETCH_ENTRIES); }

  @Override
  public void onLoadMore() {
    if (mFeedlyUtil.isAuthenticated()) {
      Intent intent = new Intent(FeedlyCacheService.ACTION_FETCH_ENTRIES,
                                 null, this, FeedlyCacheService.class);
      intent.putExtra(FeedlyCacheService.EXTRA_STREAM_ID, mNavigationFragment.getSelected());
      startService(intent);
    } else {
      Toast.makeText(this, R.string.msg_login, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onRefreshMenu() { onRefresh(FeedlyCacheService.ACTION_FETCH_SUBSCRIPTION); }

  protected void onRefresh(String action) {
    if (mFeedlyUtil.isAuthenticated()) {
      startService(new Intent(action, null, this, FeedlyCacheService.class));
    } else {
      Toast.makeText(this, R.string.msg_login, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onRefreshButton() { onRefreshList(); }

  // Call Back method  to get the ResponseUrl form other Activity
  @Override
  public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    if (requestCode == FeedlyWebAuthActivity.REQUEST_CODE && resultCode == Activity.RESULT_OK) {
      new ProcessDialogAsyncTask(this) {
        @Override
        protected void doInBackground() {
          try {
            FeedlyCredential credential = mFeedlyUtil.processResponse(getResponceUrl(data));
            //TODO: save current profile fore later use
            Profile profile = mFeedlyUtil.service().profile().get().execute();
            toast(String.format(MainActivity.this.getString(R.string.msg_signed_with),
                                profile.getEmail(), profile.getFullName()));
            commitFragment();
          } catch (Exception e) {
            toast((e instanceof HttpResponseException) ?
                  FeedlyUtil.getErrorMessage((HttpResponseException) e) : e.getMessage());
            Log.e(TAG, "Something goes wrong", e);
          }
        }

        @Override
        protected void onPostExecute() { onGroupSelected(null); }
      }.execute();
      return;
    }

    super.onActivityResult(requestCode, resultCode, data);
  }

  public void onSectionAttached(int number) {
    switch (number) {
      case 1:
        mTitle = getString(R.string.title_content);
        break;
      case 2:
        mTitle = getString(R.string.title_auth);
        break;
    }
  }

  public void restoreActionBar() {
    ActionBar actionBar = getActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setTitle(mTitle);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (!mNavigationFragment.isDrawerOpen()) {
      // Only show items in the action bar relevant to this screen
      // if the drawer is not showing. Otherwise, let the drawer
      // decide what to show in the action bar.
      getMenuInflater().inflate(R.menu.main, menu);
      restoreActionBar();
      return true;
    }
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    if (id == R.id.action_settings) {
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onFragmentInteraction(final String id) {
// TODO: Implement
  }

  public BackgroundQueryHandler getAsynchQueryHandler() { return mQueryHandler; }

  /** Fragment managing the behaviors, interactions and presentation of the navigation drawer. */
  private NavigationFragment mNavigationFragment;

  private ContentFragment mListFragment;

  /** Used to store the last screen title. For use in {@link #restoreActionBar()}. */
  private CharSequence mTitle;

  private FeedlyUtil mFeedlyUtil;

  private BackgroundQueryHandler mQueryHandler;

  private static final String KEY_PREFS_FIRST_LAUNCH = "1stlaunch";

  static final String TAG = "MainActivity";
}
