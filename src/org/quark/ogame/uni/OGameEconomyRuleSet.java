package org.quark.ogame.uni;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface OGameEconomyRuleSet {
	public enum ProductionSource {
		Base,
		MetalMine,
		CrystalMine,
		DeuteriumSynthesizer,
		Solar,
		Fusion,
		Satellite,
		Crawler,
		Plasma,
		Item,
		Geologist,
		Engineer,
		CommandingStaff,
		Collector;
	}

	public class Production {
		public final Map<ProductionSource, Integer> byType;
		public final int totalProduction;
		public final int totalConsumption;
		public final int totalNet;

		public Production(Map<ProductionSource, Integer> byType, int produced, int consumed) {
			this.byType = byType;
			this.totalProduction = produced;
			this.totalConsumption = consumed;
			this.totalNet = produced - consumed;
		}
	}

	public class FullProduction {
		public final int energy;
		public final int metal;
		public final int crystal;
		public final int deuterium;

		public FullProduction(int energy, int metal, int crystal, int deuterium) {
			this.energy = energy;
			this.metal = metal;
			this.crystal = crystal;
			this.deuterium = deuterium;
		}

		public UpgradeCost asCost() {
			return UpgradeCost.of(UpgradeType.Building, PointType.Economy, metal, crystal, deuterium, energy, Duration.ZERO);
		}
	}

	public class Requirement {
		public final AccountUpgradeType type;
		public final int level;

		public Requirement(AccountUpgradeType type, int level) {
			this.type = type;
			this.level = level;
		}
	}

	Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor);

	default FullProduction getFullProduction(Account account, Planet planet) {
		Production energyProduction = getProduction(account, planet, ResourceType.Energy, 1);
		double energyFactor = Math.min(1, energyProduction.totalProduction * 1.0 / energyProduction.totalConsumption);
		return new FullProduction(energyProduction.totalNet, //
			getProduction(account, planet, ResourceType.Metal, energyFactor).totalNet, //
			getProduction(account, planet, ResourceType.Crystal, energyFactor).totalNet, //
			getProduction(account, planet, ResourceType.Deuterium, energyFactor).totalNet);
	}
	int getSatelliteEnergy(Account account, Planet planet);
	long getStorage(Planet planet, ResourceType resourceType);
	int getMaxCrawlers(Account account, Planet planet);

	UpgradeCost getUpgradeCost(Account account, RockyBody planetOrMoon, AccountUpgradeType upgrade, int fromLevel, int toLevel);
	List<Requirement> getRequirements(AccountUpgradeType target);

	int getFields(Planet planet);
	int getFields(Moon moon);
}
