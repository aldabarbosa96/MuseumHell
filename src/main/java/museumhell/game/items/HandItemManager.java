package museumhell.game.items;

import java.util.ArrayList;
import java.util.List;

public class HandItemManager {
    private final List<HandItem> slots = new ArrayList<>(5);
    private int selected = -1;

    public HandItemManager() { for (int i=0;i<5;i++) slots.add(null); }

    public void setSlot(int idx, HandItem item) {
        if (idx>=0 && idx<slots.size()) slots.set(idx,item);
    }

    public int getSelectedIndex() { return selected; }

    public void selectSlot(int idx) {
        if (idx<0 || idx>=slots.size() || selected==idx) return;
        if (selected>=0 && slots.get(selected)!=null) slots.get(selected).onUnequip();
        selected = idx;
        var it = slots.get(selected);
        if (it!=null) it.onEquip();
    }

    public void next() { cycle(+1); }
    public void prev() { cycle(-1); }

    private void cycle(int delta) {
        int n = slots.size();
        int start = Math.max(selected, 0);
        for (int i=1;i<=n;i++) {
            int cand = Math.floorMod(start + delta*i, n);
            if (slots.get(cand)!=null) { selectSlot(cand); return; }
        }
    }

    public int getActiveSlotIndex() {
        return selected;
    }

    public HandItem getActiveItem() {
        return slots.get(selected);
    }
}
