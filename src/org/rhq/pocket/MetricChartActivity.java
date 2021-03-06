package org.rhq.pocket;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.rhq.core.domain.rest.MetricSchedule;

public class MetricChartActivity extends Activity implements Refreshable
{

    SharedPreferences preferences ;
    Dialog dialog;
    private FrameLayout progressLayout;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.metric_chart_layout);

        FragmentManager fm = getFragmentManager();
        MetricDetailContainer container= (MetricDetailContainer) fm.findFragmentById(R.id.chart_container);
        ScheduleListFragment slf = (ScheduleListFragment) fm.findFragmentById(R.id.left_picker);

        FragmentTransaction ft = fm.beginTransaction();
        if (container==null) {
            ft.add(R.id.chart_container, new ChartFragment());
        }

        if (slf==null) {
            ft.add(R.id.left_picker, new ScheduleListFragment());
        }
        ft.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // If the user has previously picked a resource, re-use it
        int resourceId = preferences.getInt("currentResourceId",-1);
        if (resourceId!=-1) {
            String resourceName = preferences.getString("currentResourceName","");
            if (!resourceName.equals("")) {
                getActionBar().setSubtitle(resourceName);
            }
            // User has already picked one, lets use it
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ScheduleListFragment fragment  =
                (ScheduleListFragment) getFragmentManager().findFragmentById(R.id.left_picker);

            if (fragment==null)
                return;

            fragment.setResourceId(resourceId);
        }


    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.metric_chart_menu,menu);
        progressLayout = (FrameLayout) menu.findItem(R.id.progress_thing).getActionView();
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        FragmentTransaction ft;
        Fragment prev;
        DialogFragment newFragment;

        // Handle item selection
        switch (item.getItemId()) {
        case R.id.preferences:
            i = new Intent(this, Preferences.class);
            startActivity(i);
            break;
        case R.id.pick_resource:

            // Taken from Android docs:
            //
            // DialogFragment.show() will take care of adding the fragment
            // in a transaction.  We also want to remove any currently showing
            // dialog, so make our own transaction and take care of that here.
            ft = getFragmentManager().beginTransaction();
            prev = getFragmentManager().findFragmentByTag("dialog");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            // Create and show the dialog.
            newFragment = new ResourcePickerFragement();
            newFragment.setCancelable(true);
            newFragment.show(ft, "dialog");
            // No need to ft.commit()
            break;

        case R.id.edit_schedule:

            if (RHQPocket.getInstance().getCurrentSchedule()==null) {
                Toast.makeText(this,getString(R.string.select_metric_first),Toast.LENGTH_SHORT).show();
            } else {

                ft = getFragmentManager().beginTransaction();
                prev = getFragmentManager().findFragmentByTag("dialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);

                // Create and show the dialog.
                newFragment = new ScheduleDetailFragment();

                MetricSchedule schedule = RHQPocket.getInstance().currentSchedule;
                ((ScheduleDetailFragment)newFragment).setSchedule(schedule);

                newFragment.setCancelable(true);
                newFragment.show(ft, "dialog");
                // No need to ft.commit()
            }
            break;

        case R.id.pick_display_range:
            ft = getFragmentManager().beginTransaction();
            prev = getFragmentManager().findFragmentByTag("dialog");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            newFragment = new MetricDisplayRangeDialogFragment();
            newFragment.setCancelable(true);
            newFragment.show(ft,"dialog");

        case android.R.id.home:
            // check if the schedule picker is visible and toggle its state
            viewHideScheduleList();
            // trigger a repaint of the chart view
            MetricDetailContainer cont = (MetricDetailContainer) getFragmentManager().findFragmentById(R.id.chart_container);
            cont.update();
            break;

        case R.id.progress_thing:
            MetricDetailContainer mcont = (MetricDetailContainer) getFragmentManager().findFragmentById(
                    R.id.chart_container);
            mcont.update();

            break;


        default:
            Log.e(getClass().getName(),"Unknown menu item :"+ item.toString());
        }
        return true;
    }

    private void viewHideScheduleList() {
        View l = findViewById(R.id.left_picker);
        if (l.getVisibility()==View.VISIBLE) {
            l.setVisibility(View.GONE);
        }
        else {
            l.setVisibility(View.VISIBLE);
        }
    }

    public void toggleChartAndTable(View v) {

        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment fragment = fm.findFragmentById(R.id.chart_container);
        Fragment newFragment;
        if (fragment instanceof ChartFragment) {
            newFragment = new MetricAggregatesFragment();
            int resourceId = preferences.getInt("currentResourceId",-1);
            if (resourceId>-1) {
                ((MetricAggregatesFragment)newFragment).setResourceId(resourceId);
                viewHideScheduleList();
            }
            else
                Toast.makeText(this,"Pick a resource first",Toast.LENGTH_SHORT).show();
        }
        else {
            newFragment = new ChartFragment();
            ((ChartFragment)newFragment).setSchedule(RHQPocket.getInstance().currentSchedule);
        }
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.remove(fragment);
        ft.add(R.id.chart_container,newFragment);
        ft.commit();


    }

    @Override
    public void showProgress() {
        if (progressLayout!=null) {

            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminate(true);
            pb.setVisibility(View.VISIBLE);

            progressLayout.removeAllViews();
            progressLayout.addView(pb);
        }
    }

    @Override
    public void hideProgress() {
        if (progressLayout!=null) {

            ImageButton ib = new ImageButton(this);
            ib.setImageResource(R.drawable.ic_menu_refresh);
            ib.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    refresh(view);
                }
            });
            progressLayout.removeAllViews();
            progressLayout.addView(ib);

        }
    }

    @Override
    public void refresh(View v) {
        MetricDetailContainer container = (MetricDetailContainer) getFragmentManager().findFragmentById(R.id.chart_container);
        if (container!=null)
            container.update();
    }
}
