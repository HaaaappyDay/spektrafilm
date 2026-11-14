# Android v1 delivery status

The offline Android editor and requested UI feedback are complete and merged to
`main`. [Parent #9](https://github.com/HaaaappyDay/spektrafilm/issues/9), its six
implementation issues (#10–15), and feedback issues #23–25 are closed. The
**Android v1** milestone is closed. PRs #17–22 and #26–29 are merged.

The Films panel uses stock-specific cartridge icons. Loading no longer changes
the fitted photo bounds. Holding the photo temporarily shows the unedited
imported image; releasing it restores the preceding view, and the toolbar
Compare toggle remains available. The Android catalog contains Portra 400,
Gold 200, and Ektar 100.

The integrated candidate passed 88 instrumented tests on each of API 29, 36,
37, and the vivo X200 Pro mini, plus 13 JVM tests, lint, and 178 scoped Python
regression tests. Physical preview p95 was 133.538ms; the slowest of ten 12MP
exports was 499.229ms. Process restoration and bounded resource review passed.
Independent review found no Critical, Important, or Minor findings. Exact
identities, measurements, limitations, APK links, and the API 29 retry history
are in the [validation report](v1-validation.md).

The verified everyday debug APK is installed on the vivo. Isolated test packages
were removed, temporary test permissions restored, and USB stay-awake remains
enabled at the owner's request. It applies while USB-powered; the power button
can still turn the screen off.

## Open backlog and stopping point

- [#16: v2 Auto](https://github.com/HaaaappyDay/spektrafilm/issues/16): not started;
  assigned to the open **Android v2** milestone. Design decisions remain open.
- [#30: API 29 CI heap mismatch](https://github.com/HaaaappyDay/spektrafilm/issues/30):
  not started. One emulator attempt exposed a 16 MiB heap; an unchanged retry
  passed with 512 MiB. The underlying startup cause remains unconfirmed.

No additional films or v2 features were implemented. Development stops here as
requested. The final reconciliation commit updates documentation only; it does
not alter the reviewed application, tests, or CI configuration.

## Rulings I made

These decisions reconcile the validation, initial UI, and feedback plans in the
order they were made. Costs describe the consequence if the decision proves wrong.

1. Reuse the generated 48MP fixture and normalization tests, extending the real
   integration flow. Cost: no changed product contract; missing coverage would
   require an additional fixture.
2. Test the current API 37 runtime without upgrading compile/target SDK.
   Cost: target-37-specific behavior is not established by compatibility tests.
3. Isolate destructive test-session resets in a separate validation application
   ID before phone integration. Cost: additional test setup and variant handling.
4. Prepare independent trace/report infrastructure while prerequisite CI ran,
   without declaring those prerequisites complete. Cost: rework if interfaces changed.
5. Complete Android-only build and evidence preparation while the phone was
   unavailable, preserving the physical acceptance gate. Cost: candidate artifacts
   might need rebuilding after physical fixes.
6. Use dependent feature branches sequentially in the existing workspace.
   Cost: branch switching and evidence reconciliation before integration.
7. Treat the confirmed Snapseed reference and autonomous-execution instruction
   as authorization for the bounded photo-first UI. Cost: later visual refinement
   might be needed where implementation choices were not explicitly specified.
8. Initially generate small thumbnails with existing CPU ColorMath and the
   checked preset repository. Cost: possible CPU/memory optimization after device
   measurement. This design was subsequently superseded by cartridge icons.
9. Implement the three explicit feedback requests without another design approval.
   Cost: cartridge artwork might need later visual adjustment.
10. Deliver feedback through separate dependent PRs without concurrent implementers.
    Cost: retargeting or rebasing before integration.
11. Measure the final physical/profileable candidate after all three feedback
    changes were integrated. Cost: the initial UI PR remained draft longer.
12. Preserve requested cartridge icons as the replacement for thumbnails.
    Cost: restoring thumbnails would require a separate change.
13. Keep Auto and additional stocks outside this delivery. Cost: those features
    remain unavailable until separately scoped and implemented.
14. Treat merge ordering and issue reconciliation as delivery work, verifying
    remote state and tested trees before closure. Cost: incorrect status would
    mislead maintainers; remote merges and ancestry were checked explicitly.

## Deferred minors

None. The independent final review reported no findings.
