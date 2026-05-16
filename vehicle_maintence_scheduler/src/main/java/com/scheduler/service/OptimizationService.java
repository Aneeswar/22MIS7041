package com.scheduler.service;

import com.scheduler.model.ScheduleResult;
import com.scheduler.model.VehicleTask;
import java.util.ArrayList;
import java.util.List;

public class OptimizationService {

    /**
     * Solves the 0/1 Knapsack problem using a space-optimized DP array.
     * 
     * @param capacity Total mechanic hours available (W).
     * @param tasks List of vehicle tasks (items).
     * @return ScheduleResult containing max impact and selected TaskIDs.
     */
    public ScheduleResult optimize(int capacity, List<VehicleTask> tasks) {
        int n = tasks.size();
        // dp[w] stores the maximum value for capacity w using a 1D array
        int[] dp = new int[capacity + 1];
        
        // keepTrack[i][w] is true if task i-1 was included for capacity w
        // This 2D boolean array is necessary for backtracking the selected items
        boolean[][] keepTrack = new boolean[n + 1][capacity + 1];

        for (int i = 1; i <= n; i++) {
            VehicleTask task = tasks.get(i - 1);
            int weight = task.duration();
            int value = task.operationalImpact();

            // Iterate backwards to optimize space and prevent multiple uses of the same item
            for (int w = capacity; w >= weight; w--) {
                if (dp[w - weight] + value > dp[w]) {
                    dp[w] = dp[w - weight] + value;
                    keepTrack[i][w] = true;
                }
            }
        }

        // Backtrack to find the specific TaskIDs
        List<String> selectedIds = new ArrayList<>();
        int currentCap = capacity;
        for (int i = n; i > 0; i--) {
            if (keepTrack[i][currentCap]) {
                VehicleTask selectedTask = tasks.get(i - 1);
                selectedIds.add(selectedTask.TaskID());
                currentCap -= selectedTask.duration();
            }
        }

        return new ScheduleResult(dp[capacity], selectedIds);
    }
}
