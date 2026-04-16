const { test, expect } = require('@playwright/test');

test.describe('Test Observatory Dashboard', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  // ==================== Page Load & Layout ====================

  test('dashboard loads with correct title and header', async ({ page }) => {
    await expect(page).toHaveTitle(/Atheryon.*Test Observatory/);
    await expect(page.locator('header .logo')).toContainText('ATHERYON');
    await expect(page.locator('header .logo')).toContainText('Test Observatory');
  });

  test('all three tabs are visible', async ({ page }) => {
    await expect(page.locator('.tab-btn', { hasText: 'Test Runner' })).toBeVisible();
    await expect(page.locator('.tab-btn', { hasText: 'Lifecycle Explorer' })).toBeVisible();
    await expect(page.locator('.tab-btn', { hasText: 'Data Inspector' })).toBeVisible();
  });

  test('Test Runner tab is active by default', async ({ page }) => {
    await expect(page.locator('.tab-btn', { hasText: 'Test Runner' })).toHaveClass(/active/);
    await expect(page.locator('#tests')).toHaveClass(/active/);
  });

  // ==================== Tab Switching ====================

  test('clicking tabs switches content', async ({ page }) => {
    // Switch to Lifecycle Explorer
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');
    await expect(page.locator('#walkthrough')).toHaveClass(/active/);
    await expect(page.locator('#tests')).not.toHaveClass(/active/);

    // Switch to Data Inspector
    await page.click('.tab-btn:has-text("Data Inspector")');
    await expect(page.locator('#inspector')).toHaveClass(/active/);
    await expect(page.locator('#walkthrough')).not.toHaveClass(/active/);

    // Switch back to Test Runner
    await page.click('.tab-btn:has-text("Test Runner")');
    await expect(page.locator('#tests')).toHaveClass(/active/);
  });

  // ==================== Test Runner ====================

  test('test catalog loads with SRS process groups', async ({ page }) => {
    // Wait for catalog to load
    await expect(page.locator('.test-group')).toHaveCount(7, { timeout: 5000 });

    // Verify SRS process IDs are present
    await expect(page.locator('.test-group-id:has-text("SRS-1")')).toBeVisible();
    await expect(page.locator('.test-group-id:has-text("SRS-2")')).toBeVisible();
    await expect(page.locator('.test-group-id:has-text("SRS-3")')).toBeVisible();
    await expect(page.locator('.test-group-id:has-text("SRS-4")')).toBeVisible();
    await expect(page.locator('.test-group-id:has-text("SRS-5")')).toBeVisible();
    await expect(page.locator('.test-group-id:has-text("SRS-6")')).toBeVisible();
    await expect(page.locator('.test-group-id:has-text("SRS-W")')).toBeVisible();

    // Verify process names
    await expect(page.locator('.test-group-header', { hasText: 'Product Enquiry' })).toBeVisible();
    await expect(page.locator('.test-group-header', { hasText: 'Application Capture' })).toBeVisible();
    await expect(page.locator('.test-group-header', { hasText: 'Decisioning' })).toBeVisible();
  });

  test('Run All button is visible and enabled', async ({ page }) => {
    const btn = page.locator('#runAllBtn');
    await expect(btn).toBeVisible();
    await expect(btn).toBeEnabled();
    await expect(btn).toHaveText('Run All');
  });

  test('console shows initial waiting message', async ({ page }) => {
    await expect(page.locator('#testConsole')).toContainText('Waiting for test run');
  });

  // ==================== Lifecycle Explorer ====================

  test('walkthrough shows state diagram with all states', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    const stateIds = [
      'DRAFT', 'IN_PROGRESS', 'READY_FOR_SUBMISSION', 'SUBMITTED',
      'UNDER_ASSESSMENT', 'VERIFIED', 'DECISIONED', 'APPROVED',
      'OFFER_ISSUED', 'OFFER_ACCEPTED', 'SETTLEMENT_IN_PROGRESS', 'SETTLED'
    ];

    for (const id of stateIds) {
      await expect(page.locator(`#state-${id}`)).toBeVisible();
    }
  });

  test('walkthrough shows 14 step dots', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');
    await expect(page.locator('.step-dot')).toHaveCount(14);
  });

  test('walkthrough starts and creates a session', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    const btn = page.locator('#walkthroughBtn');
    await expect(btn).toHaveText('Start Walkthrough');
    await btn.click();

    // Wait for session to start
    await expect(btn).toHaveText(/Next.*→/, { timeout: 10000 });

    // Session info should appear
    await expect(page.locator('#requestContent')).toContainText('Session');
    await expect(page.locator('#requestContent')).toContainText('Atheryon Home Saver Variable');
    await expect(page.locator('#responseContent')).toContainText('sessionId');
  });

  test('walkthrough step 1 creates application in DRAFT', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    // Step 1
    await page.click('#walkthroughBtn');
    await expect(page.locator('#currentStepNum')).toHaveText('1', { timeout: 10000 });

    // DRAFT state should be current
    await expect(page.locator('#state-DRAFT')).toHaveClass(/current/);

    // Step dot 1 should be active
    await expect(page.locator('#step-1')).toHaveClass(/active/);

    // API call shows POST
    await expect(page.locator('.api-method')).toContainText('POST');
    await expect(page.locator('.api-path')).toContainText('/api/v1/applications');

    // Response shows DRAFT status
    await expect(page.locator('#responseContent')).toContainText('DRAFT');
  });

  test('full 14-step walkthrough completes successfully', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start walkthrough
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    // Execute all 14 steps
    for (let step = 1; step <= 14; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Journey complete banner
    await expect(page.locator('.complete-banner')).toBeVisible();
    await expect(page.locator('.complete-banner')).toContainText('Journey Complete');

    // Final state: SETTLED
    await expect(page.locator('#responseContent')).toContainText('SETTLED');

    // Button should say Reset
    await expect(page.locator('#walkthroughBtn')).toHaveText('Reset');
  });

  test('all state nodes highlight during full walkthrough', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start + run all 14 steps
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    for (let step = 1; step <= 14; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // All states up to SETTLED should be completed (green)
    const completedStates = [
      'DRAFT', 'IN_PROGRESS', 'READY_FOR_SUBMISSION', 'SUBMITTED',
      'UNDER_ASSESSMENT', 'VERIFIED', 'DECISIONED', 'APPROVED',
      'OFFER_ISSUED', 'OFFER_ACCEPTED', 'SETTLEMENT_IN_PROGRESS', 'SETTLED'
    ];

    for (const state of completedStates) {
      await expect(page.locator(`#state-${state}`)).toHaveClass(/completed/, {
        timeout: 5000,
      });
    }
  });

  test('walkthrough shows state transitions for submission step', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start + steps 1-5
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    for (let step = 1; step <= 5; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Step 5 should show the multi-step state transition detail
    await expect(page.locator('#requestContent')).toContainText('DRAFT');
    await expect(page.locator('#requestContent')).toContainText('IN_PROGRESS');
    await expect(page.locator('#requestContent')).toContainText('READY_FOR_SUBMISSION');
    await expect(page.locator('#requestContent')).toContainText('SUBMITTED');

    // All intermediate states should be highlighted
    await expect(page.locator('#state-IN_PROGRESS')).toHaveClass(/completed/);
    await expect(page.locator('#state-READY_FOR_SUBMISSION')).toHaveClass(/completed/);
    await expect(page.locator('#state-SUBMITTED')).toHaveClass(/current/);
  });

  test('walkthrough shows decision flow detail', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start + steps 1-10
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    for (let step = 1; step <= 10; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Step 10 should show decision flow
    await expect(page.locator('#requestContent')).toContainText('automated');
    await expect(page.locator('#requestContent')).toContainText('APPROVED');

    // DECISIONED should be completed (intermediate state)
    await expect(page.locator('#state-DECISIONED')).toHaveClass(/completed/);
    // APPROVED should be current
    await expect(page.locator('#state-APPROVED')).toHaveClass(/current/);
  });

  test('walkthrough reset returns to initial state', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start + run all 14 steps
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    for (let step = 1; step <= 14; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Click Reset
    await expect(page.locator('#walkthroughBtn')).toHaveText('Reset');
    await page.click('#walkthroughBtn');

    // Should be back to initial state
    await expect(page.locator('#walkthroughBtn')).toHaveText('Start Walkthrough');
    await expect(page.locator('#currentStepNum')).toHaveText('0');

    // No states should be highlighted
    await expect(page.locator('#state-DRAFT')).not.toHaveClass(/completed|current/);
  });

  // ==================== Data Inspector ====================

  test('data inspector populates during walkthrough', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start + 3 steps (enough to have parties + securities)
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    for (let step = 1; step <= 3; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Switch to Data Inspector
    await page.click('.tab-btn:has-text("Data Inspector")');

    // Entity tree should have content
    const toggleCount = await page.locator('#entityTree .tree-toggle').count();
    expect(toggleCount).toBeGreaterThan(0);
    await expect(page.locator('#entityTree')).toContainText('LoanApplication');

    // Events timeline should have entries
    await expect(page.locator('#eventTimeline .event-item')).not.toHaveCount(0);
  });

  test('data inspector shows full entity tree after complete walkthrough', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Run full walkthrough
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    for (let step = 1; step <= 14; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Switch to Data Inspector
    await page.click('.tab-btn:has-text("Data Inspector")');

    // Should show all entity sections
    await expect(page.locator('#entityTree')).toContainText('LoanApplication');
    await expect(page.locator('#entityTree')).toContainText('Product');
    await expect(page.locator('#entityTree')).toContainText('Parties');
    await expect(page.locator('#entityTree')).toContainText('Securities');
    await expect(page.locator('#entityTree')).toContainText('Documents');
    await expect(page.locator('#entityTree')).toContainText('FinancialSnapshot');
    await expect(page.locator('#entityTree')).toContainText('DecisionRecord');
    await expect(page.locator('#entityTree')).toContainText('Offer');

    // Events should show audit trail
    await expect(page.locator('#eventTimeline')).toContainText('APPLICATION_CREATED');
    await expect(page.locator('#eventTimeline')).toContainText('APPLICATION_SUBMITTED');
  });

  // ==================== API Endpoints (direct) ====================

  test('GET /api/dev/tests returns catalog', async ({ request }) => {
    const res = await request.get('/api/dev/tests');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.catalog).toHaveLength(7);
    expect(body.catalog[0]).toHaveProperty('processId');
    expect(body.catalog[0]).toHaveProperty('className');
  });

  test('POST /api/dev/walkthrough/start creates session', async ({ request }) => {
    const res = await request.post('/api/dev/walkthrough/start');
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.sessionId).toBeTruthy();
    expect(body.totalSteps).toBe(14);
    expect(body.steps).toHaveLength(14);
    expect(body.status).toBe('ready');
    expect(body.productCreated).toBeTruthy();
  });

  test('walkthrough API executes all 14 steps', async ({ request }) => {
    // Start
    const start = await (await request.post('/api/dev/walkthrough/start')).json();
    const sessionId = start.sessionId;

    const expectedNewStates = [
      'DRAFT', 'DRAFT', 'DRAFT', 'DRAFT',                  // Steps 1-4
      'SUBMITTED', 'UNDER_ASSESSMENT',                       // Steps 5-6
      'UNDER_ASSESSMENT', 'VERIFIED',                        // Steps 7-8
      'VERIFIED', 'APPROVED',                                // Steps 9-10
      'OFFER_ISSUED', 'OFFER_ACCEPTED',                      // Steps 11-12
      'SETTLEMENT_IN_PROGRESS', 'SETTLED'                    // Steps 13-14
    ];

    for (let i = 0; i < 14; i++) {
      const res = await request.post(`/api/dev/walkthrough/${sessionId}/next`);
      expect(res.ok()).toBeTruthy();
      const step = await res.json();
      expect(step.stepNumber).toBe(i + 1);
      expect(step.stepName).toBe(start.steps[i]);
      expect(step.newState).toBe(expectedNewStates[i]);
      expect(step.applicationSnapshot).toBeTruthy();
    }

    // Verify final state
    const state = await (await request.get(`/api/dev/walkthrough/${sessionId}`)).json();
    expect(state.applicationSnapshot.status).toBe('SETTLED');
    expect(state.applicationSnapshot.offer.offerStatus).toBe('ACCEPTED');
    expect(state.history).toHaveLength(14);
  });

  test('GET /api/dev/events returns workflow events', async ({ request }) => {
    // Create a walkthrough and run a few steps
    const start = await (await request.post('/api/dev/walkthrough/start')).json();
    const sessionId = start.sessionId;

    // Run step 1
    const step1 = await (await request.post(`/api/dev/walkthrough/${sessionId}/next`)).json();
    const appId = step1.applicationSnapshot.id;

    // Fetch events
    const res = await request.get(`/api/dev/events/${appId}`);
    expect(res.ok()).toBeTruthy();
    const events = await res.json();
    expect(events.length).toBeGreaterThan(0);
    expect(events[0].eventType).toBe('APPLICATION_CREATED');
    expect(events[0].newState).toBe('DRAFT');
  });

  // ==================== Multi-Path State Machine ====================

  test('toggle shows and hides exit states', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Exit states hidden by default
    await expect(page.locator('#state-WITHDRAWN')).not.toBeVisible();
    await expect(page.locator('#state-DECLINED')).not.toBeVisible();
    await expect(page.locator('#state-LAPSED')).not.toBeVisible();
    await expect(page.locator('#state-CONDITIONALLY_APPROVED')).not.toBeVisible();

    // Toggle on
    await page.click('#diagramToggle');
    await expect(page.locator('#state-WITHDRAWN')).toBeVisible();
    await expect(page.locator('#state-DECLINED')).toBeVisible();
    await expect(page.locator('#state-LAPSED')).toBeVisible();
    await expect(page.locator('#state-CONDITIONALLY_APPROVED')).toBeVisible();

    // Toggle off
    await page.click('#diagramToggle');
    await expect(page.locator('#state-WITHDRAWN')).not.toBeVisible();
    await expect(page.locator('#state-DECLINED')).not.toBeVisible();
  });

  test('DECISIONED node has diamond shape in SVG', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');
    await expect(page.locator('#state-DECISIONED polygon')).toBeVisible();
  });

  test('hover tooltip shows transitions', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Hover over DECISIONED
    await page.hover('#state-DECISIONED');

    // Tooltip should appear with outgoing transitions
    const tooltip = page.locator('.state-tooltip');
    await expect(tooltip).toBeVisible({ timeout: 3000 });
    await expect(tooltip).toContainText('DECISIONED');
    await expect(tooltip).toContainText('APPROVED');
    await expect(tooltip).toContainText('DECLINED');
    await expect(tooltip).toContainText('COND');
  });

  test('DELETE /api/dev/walkthrough/:id cleans up session', async ({ request }) => {
    const start = await (await request.post('/api/dev/walkthrough/start')).json();
    const sessionId = start.sessionId;

    const del = await request.delete(`/api/dev/walkthrough/${sessionId}`);
    expect(del.status()).toBe(204);

    // Session should be gone
    const get = await request.get(`/api/dev/walkthrough/${sessionId}`);
    expect(get.status()).toBe(404);
  });

  // ==================== Lifecycle Explorer — New Features ====================

  test('back navigation shows previous step data', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start walkthrough
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    // Execute 3 steps
    for (let step = 1; step <= 3; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Click Back
    await page.click('#transportBack');

    // Viewing indicator should appear
    await expect(page.locator('#viewingIndicator')).toBeVisible();
    await expect(page.locator('#viewingStepNum')).toHaveText('2');

    // Request pane should show step 2 data (Add Borrower → /api/v1/parties)
    await expect(page.locator('#requestContent')).toContainText('/api/v1/parties');
  });

  test('clicking completed node shows historical data', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start + execute 5 steps (reaches SUBMITTED)
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    for (let step = 1; step <= 5; step++) {
      await page.click('#walkthroughBtn');
      await expect(page.locator('#currentStepNum')).toHaveText(String(step), { timeout: 15000 });
    }

    // Click the completed DRAFT node via JS dispatch (SVG <g> elements need this in Playwright)
    await page.locator('#state-DRAFT').dispatchEvent('click');

    // Should show viewing highlight and historical data
    await expect(page.locator('#state-DRAFT')).toHaveClass(/viewing/);
    await expect(page.locator('#viewingIndicator')).toBeVisible();
  });

  test('auto-play advances through steps', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');

    // Start walkthrough
    await page.click('#walkthroughBtn');
    await expect(page.locator('#walkthroughBtn')).toHaveText(/Next.*→/, { timeout: 10000 });

    // Execute first step manually
    await page.click('#walkthroughBtn');
    await expect(page.locator('#currentStepNum')).toHaveText('1', { timeout: 15000 });

    // Click Auto
    await page.click('#autoPlayBtn');
    await expect(page.locator('#autoPlayBtn')).toHaveClass(/active/);

    // Wait for auto-play to advance a few steps (at 1.5s intervals)
    await expect(page.locator('#currentStepNum')).not.toHaveText('1', { timeout: 10000 });

    // Step should have advanced
    const stepText = await page.locator('#currentStepNum').textContent();
    expect(parseInt(stepText)).toBeGreaterThan(1);
  });

  test('State Machine tab does not exist', async ({ page }) => {
    await expect(page.locator('.tab-btn:has-text("State Machine")')).toHaveCount(0);
    await expect(page.locator('#statemachine')).toHaveCount(0);
  });

  test('SVG diagram is visible in Lifecycle Explorer', async ({ page }) => {
    await page.click('.tab-btn:has-text("Lifecycle Explorer")');
    await expect(page.locator('#explorerSvg')).toBeVisible();

    // Should have state nodes as SVG groups
    await expect(page.locator('#state-DRAFT')).toBeVisible();
    await expect(page.locator('#state-SETTLED')).toBeVisible();

    // Should have edges
    const edgeCount = await page.locator('.sm-edge').count();
    expect(edgeCount).toBeGreaterThan(0);
  });
});
