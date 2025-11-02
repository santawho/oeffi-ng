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

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import de.schildbach.oeffi.Application;
import de.schildbach.pte.AvvAachenProvider;
import de.schildbach.pte.AvvAugsburgProvider;
import de.schildbach.pte.BartProvider;
import de.schildbach.pte.BayernProvider;
import de.schildbach.pte.BsvagProvider;
import de.schildbach.pte.BvgProvider;
import de.schildbach.pte.CmtaProvider;
import de.schildbach.pte.DbMovasProvider;
import de.schildbach.pte.DbProvider;
import de.schildbach.pte.DeutschlandTicketProvider;
import de.schildbach.pte.DingProvider;
import de.schildbach.pte.DsbProvider;
import de.schildbach.pte.DubProvider;
import de.schildbach.pte.GvhProvider;
import de.schildbach.pte.InvgProvider;
import de.schildbach.pte.KvvProvider;
import de.schildbach.pte.LinzProvider;
import de.schildbach.pte.LuProvider;
import de.schildbach.pte.MerseyProvider;
import de.schildbach.pte.MvgProvider;
import de.schildbach.pte.MvvProvider;
import de.schildbach.pte.NasaProvider;
import de.schildbach.pte.NetworkApiProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NsProvider;
import de.schildbach.pte.NvbwProvider;
import de.schildbach.pte.NvvProvider;
import de.schildbach.pte.OebbProvider;
import de.schildbach.pte.PlProvider;
import de.schildbach.pte.RmvProvider;
import de.schildbach.pte.RtProvider;
import de.schildbach.pte.RtaChicagoProvider;
import de.schildbach.pte.SeProvider;
import de.schildbach.pte.ShProvider;
import de.schildbach.pte.StvProvider;
import de.schildbach.pte.SydneyProvider;
import de.schildbach.pte.TlemProvider;
import de.schildbach.pte.VbbProvider;
import de.schildbach.pte.VblProvider;
import de.schildbach.pte.VbnProvider;
import de.schildbach.pte.VgnProvider;
import de.schildbach.pte.VmtProvider;
import de.schildbach.pte.VmvProvider;
import de.schildbach.pte.VrnProvider;
import de.schildbach.pte.VrrProvider;
import de.schildbach.pte.VrsProvider;
import de.schildbach.pte.VvmProvider;
import de.schildbach.pte.VvoProvider;
import de.schildbach.pte.VvsProvider;
import de.schildbach.pte.VvvProvider;
import de.schildbach.pte.WienProvider;
import de.schildbach.pte.ZvvProvider;
import okhttp3.HttpUrl;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NetworkProviderFactory {
    private static Map<NetworkId, NetworkProvider> providerCache = new HashMap<>();
    private static final BaseEncoding BASE64 = BaseEncoding.base64();

    private static final byte[] VRS_CLIENT_CERTIFICATE = BASE64.decode("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");

    public static synchronized NetworkProvider provider(final NetworkId networkId) {
        final NetworkProvider cachedNetworkProvider = providerCache.get(networkId);
        if (cachedNetworkProvider != null)
            return cachedNetworkProvider;

        final NetworkApiProvider networkProvider = forId(networkId);
        if (networkId != NetworkId.PL)
            networkProvider.setUserAgent(Application.getUserAgent());
        networkProvider.setUserInterfaceLanguage(Locale.getDefault().getLanguage());
        networkProvider.setMessagesAsSimpleHtml(true);
        providerCache.put(networkId, networkProvider);
        return networkProvider;
    }

    private static NetworkApiProvider forId(final NetworkId networkId) {
        if (networkId.equals(NetworkId.RT))
            return new RtProvider();
        else if (networkId.equals(NetworkId.DEUTSCHLANDTICKET))
            return new DeutschlandTicketProvider();
        else if (networkId.equals(NetworkId.DB))
            return new DbProvider.Fernverkehr();
        else if (networkId.equals(NetworkId.DBREGIO))
            return new DbProvider.Regio();
        else if (networkId.equals(NetworkId.DBINTERNATIONAL))
            return new DbProvider.International();
//        else if (networkId.equals(NetworkId.DBDEUTSCHLANDTICKETWEB))
//            return new DbWebProvider.DeutschlandTicket();
//        else if (networkId.equals(NetworkId.DBWEB))
//            return new DbWebProvider.Fernverkehr();
//        else if (networkId.equals(NetworkId.DBREGIOWEB))
//            return new DbWebProvider.Regio();
        else if (networkId.equals(NetworkId.DBDEUTSCHLANDTICKETMOVAS))
            return new DbMovasProvider.DeutschlandTicket();
        else if (networkId.equals(NetworkId.DBMOVAS))
            return new DbMovasProvider.Fernverkehr();
        else if (networkId.equals(NetworkId.DBREGIOMOVAS))
            return new DbMovasProvider.Regio();
//        else if (networkId.equals(NetworkId.DBHAFAS))
//            return new DbHafasProvider.Fernverkehr("{\"type\":\"AID\",\"aid\":\"n91dB8Z77MLdoR0K\"}",
//                    "bdI8UVj40K5fvxwf".getBytes(Charsets.UTF_8));
//        else if (networkId.equals(NetworkId.DBREGIOHAFAS))
//            return new DbHafasProvider.Regio("{\"type\":\"AID\",\"aid\":\"n91dB8Z77MLdoR0K\"}",
//                    "bdI8UVj40K5fvxwf".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.BVG))
            return new BvgProvider("{\"aid\":\"1Rxs112shyHLatUX4fofnmdxK\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.VBB))
            return new VbbProvider("{\"type\":\"AID\",\"aid\":\"hafas-vbb-apps\"}",
                    "RCTJM2fFxFfxxQfI".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.NVV))
            return new NvvProvider("{\"type\":\"AID\",\"aid\":\"Kt8eNOH7qjVeSxNA\"}");
        else if (networkId.equals(NetworkId.BAYERN))
            return new BayernProvider();
        else if (networkId.equals(NetworkId.MVV))
            return new MvvProvider();
        else if (networkId.equals(NetworkId.INVG))
            return new InvgProvider("{\"type\":\"AID\",\"aid\":\"GITvwi3BGOmTQ2a5\"}",
                    "ERxotxpwFT7uYRsI".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.AVV_AUGSBURG))
            return new AvvAugsburgProvider("{\"type\":\"AID\",\"aid\":\"jK91AVVZU77xY5oH\"}");
        else if (networkId.equals(NetworkId.VGN))
            return new VgnProvider(HttpUrl.parse("https://efa.vgn.de/vgnExt_oeffi/"));
        else if (networkId.equals(NetworkId.VVM))
            return new VvmProvider();
        else if (networkId.equals(NetworkId.VMV))
            return new VmvProvider();
        else if (networkId.equals(NetworkId.SH))
            return new ShProvider("{\"aid\":\"r0Ot9FLFNAFxijLW\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.GVH))
            return new GvhProvider();
        else if (networkId.equals(NetworkId.BSVAG))
            return new BsvagProvider();
        else if (networkId.equals(NetworkId.VBN))
            return new VbnProvider("{\"aid\":\"rnOHBWhesvc7gFkd\",\"type\":\"AID\"}",
                    "SP31mBufSyCLmNxp".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.NASA))
            return new NasaProvider("{\"type\":\"AID\",\"aid\":\"nasa-apps\"}");
        else if (networkId.equals(NetworkId.RMV))
            return new RmvProvider("{\"type\":\"AID\",\"aid\":\"ikfr894fkfddXxA0U\"}");
        else if (networkId.equals(NetworkId.VMT))
            return new VmtProvider("{\"aid\":\"vj5d7i3g9m5d7e3\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.VVO))
            return new VvoProvider(HttpUrl.parse("https://efa.vvo-online.de/Oeffi/"));
        else if (networkId.equals(NetworkId.VRR))
            return new VrrProvider();
        else if (networkId.equals(NetworkId.VRS))
            return new VrsProvider(VRS_CLIENT_CERTIFICATE);
        else if (networkId.equals(NetworkId.AVV_AACHEN))
            return new AvvAachenProvider("{\"id\":\"AVV_AACHEN\",\"l\":\"vs_oeffi\",\"type\":\"WEB\"}",
                    "{\"type\":\"AID\",\"aid\":\"4vV1AcH3N511icH\"}");
        else if (networkId.equals(NetworkId.MVG))
            return new MvgProvider();
        else if (networkId.equals(NetworkId.VRN))
            return new VrnProvider();
        else if (networkId.equals(NetworkId.VVS))
            return new VvsProvider();
        else if (networkId.equals(NetworkId.DING))
            return new DingProvider();
        else if (networkId.equals(NetworkId.KVV))
            return new KvvProvider(HttpUrl.parse("https://projekte.kvv-efa.de/oeffi/"));
        else if (networkId.equals(NetworkId.NVBW))
            return new NvbwProvider();
        else if (networkId.equals(NetworkId.VVV))
            return new VvvProvider();
        else if (networkId.equals(NetworkId.OEBB))
            return new OebbProvider("{\"type\":\"AID\",\"aid\":\"OWDL4fE4ixNiPBBm\"}");
        else if (networkId.equals(NetworkId.WIEN))
            return new WienProvider();
        else if (networkId.equals(NetworkId.LINZ))
            return new LinzProvider();
        else if (networkId.equals(NetworkId.STV))
            return new StvProvider();
        else if (networkId.equals(NetworkId.VBL))
            return new VblProvider();
        else if (networkId.equals(NetworkId.ZVV))
            return new ZvvProvider("{\"type\":\"AID\",\"aid\":\"hf7mcf9bv3nv8g5f\"}");
        else if (networkId.equals(NetworkId.LU))
            return new LuProvider("{\"type\":\"AID\",\"aid\":\"SkC81GuwuzL4e0\"}");
        else if (networkId.equals(NetworkId.NS))
            return new NsProvider();
        else if (networkId.equals(NetworkId.DSB))
            return new DsbProvider("{\"type\":\"AID\",\"aid\":\"irkmpm9mdznstenr-android\"}");
        else if (networkId.equals(NetworkId.SE))
            return new SeProvider("{\"type\":\"AID\",\"aid\":\"h5o3n7f4t2m8l9x1\"}");
        else if (networkId.equals(NetworkId.TLEM))
            return new TlemProvider();
        else if (networkId.equals(NetworkId.MERSEY))
            return new MerseyProvider();
        else if (networkId.equals(NetworkId.PL))
            return new PlProvider("{\"type\":\"AID\",\"aid\":\"DrxJYtYZQpEBCtcb\"}");
        else if (networkId.equals(NetworkId.DUB))
            return new DubProvider();
        else if (networkId.equals(NetworkId.BART))
            return new BartProvider("{\"type\":\"AID\",\"aid\":\"kEwHkFUCIL500dym\"}");
        else if (networkId.equals(NetworkId.RTACHICAGO))
            return new RtaChicagoProvider();
        else if (networkId.equals(NetworkId.CMTA))
            return new CmtaProvider("{\"type\":\"AID\",\"aid\":\"web9j2nak29uz41irb\"}");
        else if (networkId.equals(NetworkId.SYDNEY))
            return new SydneyProvider();
        else
            throw new IllegalArgumentException(networkId.name());
    }
}
