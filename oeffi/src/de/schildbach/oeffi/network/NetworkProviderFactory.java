/*
 * Copyright the original author or authors.
 *
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.network;

import android.content.SharedPreferences;
import android.util.Base64;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.pte.provider.hafas.AvvAachenProvider;
import de.schildbach.pte.provider.hafas.AvvAugsburgProvider;
import de.schildbach.pte.provider.hafas.BartProvider;
import de.schildbach.pte.provider.hafas.BvgProvider;
import de.schildbach.pte.provider.db.DbHafasProvider;
import de.schildbach.pte.provider.hafas.DsbProvider;
import de.schildbach.pte.provider.hafas.InvgProvider;
import de.schildbach.pte.provider.efa.KvvProvider;
import de.schildbach.pte.provider.hafas.LuProvider;
import de.schildbach.pte.provider.hafas.NasaProvider;
import de.schildbach.pte.provider.NetworkApiProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.provider.hafas.NvvProvider;
import de.schildbach.pte.provider.hafas.OebbProvider;
import de.schildbach.pte.provider.hafas.PlProvider;
import de.schildbach.pte.provider.hafas.SeProvider;
import de.schildbach.pte.provider.hafas.ShProvider;
import de.schildbach.pte.Standard;
import de.schildbach.pte.provider.hafas.VbbProvider;
import de.schildbach.pte.provider.hafas.VbnProvider;
import de.schildbach.pte.provider.efa.VgnProvider;
import de.schildbach.pte.provider.hafas.VgsProvider;
import de.schildbach.pte.provider.hafas.VmtProvider;
import de.schildbach.pte.provider.other.VrsProvider;
import de.schildbach.pte.provider.efa.VvoProvider;
import de.schildbach.pte.provider.hafas.ZvvProvider;
import okhttp3.HttpUrl;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NetworkProviderFactory extends de.schildbach.pte.NetworkProviderFactory {
    private static final byte[] VRS_CLIENT_CERTIFICATE = Base64.decode("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", Base64.DEFAULT);

    private static final NetworkProviderFactory instance = new NetworkProviderFactory();

    public static NetworkProviderFactory getInstance() {
        return instance;
    }

    public static synchronized NetworkProvider provider(final NetworkId networkId) {
        return instance.getNetworkProvider(networkId);
    }

    private final Map<NetworkId, NetworkProvider> providerCache = new HashMap<>();

    private void setupStandard() {
        final SharedPreferences preferences = Application.getInstance().getSharedPreferences();
        Standard.setDoNotUseSpecialLineStyles(preferences
                .getBoolean(Constants.PREFS_KEY_USER_INTERFACE_NOPROVIDERCOLORS_ENABLED, false));
        Standard.setPreferPredefinedLineStyles(preferences
                .getBoolean(Constants.PREFS_KEY_USER_INTERFACE_PREFERPREDEFINEDCOLORS_ENABLED, false));
    }

    @Override
    public NetworkProvider getNetworkProvider(final NetworkId networkId) {
        setupStandard();
        final NetworkProvider cachedNetworkProvider = providerCache.get(networkId);
        if (cachedNetworkProvider != null)
            return cachedNetworkProvider;

        final NetworkProvider networkProvider = super.getNetworkProvider(networkId);
        if (networkProvider instanceof NetworkApiProvider) {
            final NetworkApiProvider networkApiProvider = (NetworkApiProvider) networkProvider;
            if (networkId != NetworkId.PL)
                networkApiProvider.setUserAgent(Application.getUserAgent());
            networkApiProvider.setUserInterfaceLanguage(Locale.getDefault().getLanguage());
            networkApiProvider.setMessagesAsSimpleHtml(true);
        }
        providerCache.put(networkId, networkProvider);
        return networkProvider;
    }

    {
        addConfigurator(VrsProvider.class, () -> new VrsProvider(VRS_CLIENT_CERTIFICATE));
        addConfigurator(DbHafasProvider.Fernverkehr.class, () -> new DbHafasProvider.Fernverkehr("{\"type\":\"AID\",\"aid\":\"n91dB8Z77MLdoR0K\"}", "bdI8UVj40K5fvxwf".getBytes(StandardCharsets.UTF_8)));
        addConfigurator(DbHafasProvider.Regio.class, () -> new DbHafasProvider.Regio("{\"type\":\"AID\",\"aid\":\"n91dB8Z77MLdoR0K\"}", "bdI8UVj40K5fvxwf".getBytes(StandardCharsets.UTF_8)));
        addConfigurator(BvgProvider.class, () -> new BvgProvider("{\"aid\":\"1Rxs112shyHLatUX4fofnmdxK\",\"type\":\"AID\"}"));
        addConfigurator(VbbProvider.class, () -> new VbbProvider("{\"type\":\"AID\",\"aid\":\"hafas-vbb-apps\"}"));
        addConfigurator(NvvProvider.class, () -> new NvvProvider("{\"type\":\"AID\",\"aid\":\"Kt8eNOH7qjVeSxNA\"}"));
//        addConfigurator(RmvProvider.class, () -> new RmvProvider("{\"type\":\"AID\",\"aid\":\"ikfr894fkfddXxA0U\"}"));
        addConfigurator(InvgProvider.class, () -> new InvgProvider("{\"type\":\"AID\",\"aid\":\"GITvwi3BGOmTQ2a5\"}"));
        addConfigurator(AvvAugsburgProvider.class, () -> new AvvAugsburgProvider("{\"type\":\"AID\",\"aid\":\"jK91AVVZU77xY5oH\"}"));
        addConfigurator(VgnProvider.class, () -> new VgnProvider(HttpUrl.parse("https://efa.vgn.de/vgnExt_oeffi/")));
        addConfigurator(ShProvider.class, () -> new ShProvider("{\"aid\":\"r0Ot9FLFNAFxijLW\",\"type\":\"AID\"}"));
        addConfigurator(VbnProvider.class, () -> new VbnProvider("{\"aid\":\"rnOHBWhesvc7gFkd\",\"type\":\"AID\"}"));
        addConfigurator(NasaProvider.class, () -> new NasaProvider("{\"type\":\"AID\",\"aid\":\"nasa-apps\"}"));
        addConfigurator(VmtProvider.class, () -> new VmtProvider("{\"aid\":\"vj5d7i3g9m5d7e3\",\"type\":\"AID\"}"));
        addConfigurator(VvoProvider.class, () -> new VvoProvider(HttpUrl.parse("https://efa.vvo-online.de/Oeffi/")));
        addConfigurator(AvvAachenProvider.class, () -> new AvvAachenProvider("{\"id\":\"AVV_AACHEN\",\"l\":\"vs_oeffi\",\"type\":\"WEB\"}","{\"type\":\"AID\",\"aid\":\"4vV1AcH3N511icH\"}"));
        addConfigurator(VgsProvider.class, () -> new VgsProvider("{\"type\":\"AID\",\"aid\":\"yCW9qZFSye1wIv3gCzm5r7d2kJ3LIF\"}"));
        addConfigurator(KvvProvider.class, () -> new KvvProvider(HttpUrl.parse("https://projekte.kvv-efa.de/oeffi/")));
        addConfigurator(OebbProvider.class, () -> new OebbProvider("{\"type\":\"AID\",\"aid\":\"OWDL4fE4ixNiPBBm\"}"));
        addConfigurator(ZvvProvider.class, () -> new ZvvProvider("{\"type\":\"AID\",\"aid\":\"hf7mcf9bv3nv8g5f\"}"));
        addConfigurator(LuProvider.class, () -> new LuProvider("{\"type\":\"AID\",\"aid\":\"SkC81GuwuzL4e0\"}"));
//        addConfigurator(DsbProvider.class, () -> new DsbProvider("{\"type\":\"AID\",\"aid\":\"irkmpm9mdznstenr-android\"}"));
        addConfigurator(SeProvider.class, () -> new SeProvider("{\"type\":\"AID\",\"aid\":\"h5o3n7f4t2m8l9x1\"}"));
        addConfigurator(PlProvider.class, () -> new PlProvider("{\"type\":\"AID\",\"aid\":\"DrxJYtYZQpEBCtcb\"}"));
        addConfigurator(BartProvider.class, () -> new BartProvider("{\"type\":\"AID\",\"aid\":\"kEwHkFUCIL500dym\"}"));
//        addConfigurator(VorProvider.class, NetworkId.State.unselectable);
//        addConfigurator(OoevvProvider.class, NetworkId.State.unselectable);
//        addConfigurator(VvtProvider.class, NetworkId.State.unselectable);
//        addConfigurator(SvvProvider.class, NetworkId.State.unselectable);
//        addConfigurator(VmobilProvider.class, NetworkId.State.unselectable);
    }
}
